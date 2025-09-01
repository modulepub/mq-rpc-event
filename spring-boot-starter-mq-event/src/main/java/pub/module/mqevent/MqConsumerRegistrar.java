package pub.module.mqevent;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 扫描 {@code XxxConsumer} 槽位实现类，在 Bean 实例化前注册 Stream Function 单例与 inbound binding。
 * <p>
 * 扫描阶段仅用 ASM 读取元数据，不对全部 Bean 做 {@code Class.forName}，避免损坏的 .class 拖垮启动。
 */
public class MqConsumerRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, Ordered {

    private static final String BINDINGS_PREFIX = "spring.cloud.stream.bindings.";
    private static final String FUNCTION_DEFINITION = "spring.cloud.function.definition";
    private static final String PROPERTY_SOURCE = "tgMqConsumerStream";

    private ConfigurableEnvironment environment;
    private final List<MqSubscription> pendingSubscriptions = new ArrayList<>();
    private static final List<FunctionRegistration<?>> PENDING_REGISTRATIONS = new ArrayList<>();
    private static final List<MqSubscription> REGISTERED_SUBSCRIPTIONS = new ArrayList<>();

    static List<MqSubscription> registeredSubscriptions() {
        synchronized (REGISTERED_SUBSCRIPTIONS) {
            return List.copyOf(REGISTERED_SUBSCRIPTIONS);
        }
    }

    static List<FunctionRegistration<?>> drainRegistrations() {
        synchronized (PENDING_REGISTRATIONS) {
            if (PENDING_REGISTRATIONS.isEmpty()) {
                return List.of();
            }
            List<FunctionRegistration<?>> copy = List.copyOf(PENDING_REGISTRATIONS);
            PENDING_REGISTRATIONS.clear();
            return copy;
        }
    }

    @Override
    public void setEnvironment(org.springframework.core.env.Environment environment) {
        if (environment instanceof ConfigurableEnvironment configurableEnvironment) {
            this.environment = configurableEnvironment;
        }
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        pendingSubscriptions.clear();
        ClassLoader classLoader = ClassUtils.getDefaultClassLoader();
        MetadataReaderFactory readerFactory = new CachingMetadataReaderFactory(classLoader);
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
        Set<String> subscriptionKeys = new HashSet<>();

        for (String beanName : registry.getBeanDefinitionNames()) {
            String className = registry.getBeanDefinition(beanName).getBeanClassName();
            if (className == null) {
                continue;
            }
            Resource resource = resolver.getResource(
                    PathMatchingResourcePatternResolver.CLASSPATH_URL_PREFIX
                            + ClassUtils.convertClassNameToResourcePath(className) + ".class");
            if (!resource.isReadable()) {
                continue;
            }
            MetadataReader reader;
            try {
                reader = readerFactory.getMetadataReader(resource);
            } catch (Exception ex) {
                continue;
            }
            if (reader.getClassMetadata().isInterface() || reader.getClassMetadata().isAnnotation()) {
                continue;
            }
            Optional<MqSubscription> subscriptionOptional = MqContractSupport.findSubscriptionFromMetadata(
                    List.of(reader.getClassMetadata().getInterfaceNames()),
                    className,
                    beanName,
                    classLoader);
            if (subscriptionOptional.isEmpty()) {
                continue;
            }
            MqSubscription subscription = subscriptionOptional.get();

            String subscriptionKey = subscription.channel().destination() + "#" + subscription.group();
            if (!subscriptionKeys.add(subscriptionKey)) {
                throw new IllegalStateException(
                        "重复 MQ 订阅 destination=" + subscription.channel().destination()
                                + " group=" + subscription.group()
                                + " handler=" + className);
            }
            if (registry.containsBeanDefinition(subscription.function())) {
                throw new IllegalStateException("MQ Function 名冲突: " + subscription.function()
                        + " handler=" + className);
            }
            pendingSubscriptions.add(subscription);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        synchronized (REGISTERED_SUBSCRIPTIONS) {
            REGISTERED_SUBSCRIPTIONS.clear();
        }
        if (pendingSubscriptions.isEmpty() || environment == null) {
            return;
        }
        synchronized (REGISTERED_SUBSCRIPTIONS) {
            REGISTERED_SUBSCRIPTIONS.addAll(pendingSubscriptions);
        }
        Map<String, Object> properties = new HashMap<>();
        Set<String> functionNames = new LinkedHashSet<>();
        ClassLoader classLoader = beanFactory.getBeanClassLoader();
        for (MqSubscription subscription : pendingSubscriptions) {
            Class<?> handlerClass = MqContractSupport.resolveClass(subscription.handlerClassName(), classLoader);
            if (handlerClass == null) {
                throw new IllegalStateException("MQ Handler 类无法加载: " + subscription.handlerClassName());
            }
            MqContractSupport.validateHandlerClass(handlerClass);
            Object consumer = createStreamFunction(beanFactory, subscription);
            if (!beanFactory.containsSingleton(subscription.function())) {
                beanFactory.registerSingleton(subscription.function(), consumer);
            }
            synchronized (PENDING_REGISTRATIONS) {
                PENDING_REGISTRATIONS.add(createFunctionRegistration(consumer, subscription));
            }
            registerInboundBinding(properties, subscription);
            functionNames.add(subscription.function());
        }
        properties.put(FUNCTION_DEFINITION, String.join(";", functionNames));
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, properties));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static Object createStreamFunction(ConfigurableListableBeanFactory beanFactory, MqSubscription subscription) {
        MqChannelMetadata channel = subscription.channel();
        String handlerBeanName = subscription.handlerBeanName();
        if (subscription.requestReply()) {
            return (Function<Object, Object>) payload -> {
                Object handler = beanFactory.getBean(handlerBeanName);
                return MqContractSupport.invokeHandler(handler, channel, payload);
            };
        }
        return (Consumer<Object>) payload -> {
            Object handler = beanFactory.getBean(handlerBeanName);
            MqContractSupport.invokeHandler(handler, channel, payload);
        };
    }

    private static FunctionRegistration<?> createFunctionRegistration(
            Object target, MqSubscription subscription) {
        MqChannelMetadata channel = subscription.channel();
        String functionName = subscription.function();
        if (subscription.requestReply()) {
            Type type = FunctionTypeUtils.functionType(
                    ResolvableType.forClass(channel.payloadType()).getType(),
                    ResolvableType.forClass(channel.replyType()).getType());
            return new FunctionRegistration<>((Function<Object, Object>) target, functionName).type(type);
        }
        Type type = FunctionTypeUtils.consumerType(ResolvableType.forClass(channel.payloadType()).getType());
        return new FunctionRegistration<>((Consumer<Object>) target, functionName).type(type);
    }

    private static void registerInboundBinding(Map<String, Object> properties, MqSubscription subscription) {
        String inBinding = subscription.inBinding();
        properties.put(BINDINGS_PREFIX + inBinding + ".destination", subscription.channel().destination());
        properties.put(BINDINGS_PREFIX + inBinding + ".group", subscription.group());
    }
}
