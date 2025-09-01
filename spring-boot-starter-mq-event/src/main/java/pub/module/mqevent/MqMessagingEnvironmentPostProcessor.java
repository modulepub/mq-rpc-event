package pub.module.mqevent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 扫描 classpath 中 {@link MqChannel} 契约，注册生产者 outbound binding（零域硬编码）。
 */
public class MqMessagingEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String BINDINGS_PREFIX = "spring.cloud.stream.bindings.";
    private static final String PROPERTY_SOURCE = "tgMqProducerBindings";
    private static final String BASE_PACKAGE = "pub.module";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        ClassLoader classLoader = application.getClassLoader();
        Set<Class<?>> channelInterfaces = findMqChannelInterfaces(classLoader);
        if (channelInterfaces.isEmpty()) {
            return;
        }
        Map<String, Object> properties = new HashMap<>();
        for (Class<?> channelInterface : channelInterfaces) {
            MqChannelMetadata metadata = MqContractSupport.getChannelMetadata(channelInterface);
            properties.put(
                    BINDINGS_PREFIX + metadata.outBinding() + ".destination",
                    metadata.destination());
        }
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, properties));
    }

    private static Set<Class<?>> findMqChannelInterfaces(ClassLoader classLoader) {
        Set<Class<?>> results = new HashSet<>();
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
            String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
                    + ClassUtils.convertClassNameToResourcePath(BASE_PACKAGE) + "/**/*.class";
            Resource[] resources = resolver.getResources(pattern);
            MetadataReaderFactory readerFactory = new CachingMetadataReaderFactory(resolver);
            for (Resource resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                MetadataReader reader = readerFactory.getMetadataReader(resource);
                if (!reader.getClassMetadata().isInterface()) {
                    continue;
                }
                if (!reader.getAnnotationMetadata().hasAnnotation(MqChannel.class.getName())) {
                    continue;
                }
                String className = reader.getClassMetadata().getClassName();
                Class<?> type = ClassUtils.forName(className, classLoader);
                if (MqContractSupport.isRootContract(type)) {
                    results.add(type);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("扫描 @MqChannel 契约失败", ex);
        }
        return results;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 11;
    }
}
