package pub.module.mqrpc.autoconfigure;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.PlatformTransactionManager;
import tg.rpc.mqrpc.admin.RpcTxAdminController;
import tg.rpc.mqrpc.admin.RpcTxAdminPageController;
import pub.module.mqrpc.autoconfigure.support.DefaultRpcExceptionConverter;
import pub.module.mqrpc.autoconfigure.support.OptionalBizExceptionRpcSupport;
import pub.module.mqrpc.spi.RpcClientExceptionConverter;
import pub.module.mqrpc.spi.RpcExceptionConverter;
import pub.module.mqrpc.tx.InMemoryRpcTxRecordRepository;
import pub.module.mqrpc.tx.RpcTxCompensator;
import pub.module.mqrpc.tx.RpcTxCoordinator;
import pub.module.mqrpc.tx.RpcTxRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * ModuleApi MQ-RPC 自动配置（Feign 等价层，底层 MQ Request-Reply）。
 * <p>仅当 classpath 存在 {@link pub.module.mqrpc.RpcApi} 契约时启用；路由固定为 AUTO（本地 Provider 优先）。</p>
 */
@AutoConfiguration(after = EmbeddedRabbitAutoConfiguration.class)
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnRpcApiPresent
@EnableConfigurationProperties(ModuleCallProperties.class)
@Import(ModuleApiClientRegistrar.class)
@Slf4j
public class ModuleRpcAutoConfiguration {

    @Bean(name = ModuleApiProxyCreator.BEAN_NAME)
    public ModuleApiProxyCreator moduleApiProxyCreator(org.springframework.context.ApplicationContext applicationContext) {
        return new ModuleApiProxyCreator(applicationContext);
    }

    @Bean
    public static ModuleApiProviderRegistry moduleApiProviderRegistry(
            ObjectProvider<RpcExceptionConverter> converters,
            ObjectProvider<PlatformTransactionManager> transactionManagers) {
        List<RpcExceptionConverter> resolved = new ArrayList<>();
        converters.orderedStream().forEach(resolved::add);
        if (resolved.stream().noneMatch(c -> c instanceof DefaultRpcExceptionConverter)) {
            resolved.add(new DefaultRpcExceptionConverter());
        }
        RpcExceptionConverter biz = OptionalBizExceptionRpcSupport.serverConverterIfPresent();
        if (biz != null) {
            resolved.add(biz);
        }
        return new ModuleApiProviderRegistry(resolved, transactionManagers.getIfAvailable());
    }

    @Bean
    static ModuleApiProviderBootstrap moduleApiProviderBootstrap(
            ConfigurableListableBeanFactory beanFactory,
            ModuleApiProviderRegistry providerRegistry) {
        return new ModuleApiProviderBootstrap(beanFactory, providerRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(RpcTxRecordRepository.class)
    public RpcTxRecordRepository rpcTxRecordRepository() {
        return new InMemoryRpcTxRecordRepository();
    }

    @Bean
    public RpcTxCompensator rpcTxCompensator(
            ModuleApiProviderRegistry providerRegistry,
            MqRpcGateway mqRpcGateway,
            ObjectProvider<ObjectMapper> objectMapper) {
        return new RpcTxCompensator(providerRegistry, mqRpcGateway, objectMapper.getIfAvailable());
    }

    @Bean
    public RpcTxCoordinator rpcTxCoordinator(
            ModuleCallProperties properties,
            RpcTxRecordRepository repository,
            RpcTxCompensator compensator,
            ObjectProvider<ObjectMapper> objectMapper) {
        ObjectMapper mapper = objectMapper.getIfAvailable();
        if (mapper == null) {
            mapper = new ObjectMapper();
        }
        return new RpcTxCoordinator(repository, compensator, mapper, properties.getTx().isEnabled());
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "tg.module-call.tx", name = "admin-enabled", havingValue = "true", matchIfMissing = true)
    public RpcTxAdminPageController rpcTxAdminPageController() {
        log.info("RPC tx admin page registered at {}", RpcTxAdminPaths.BASE);
        return new RpcTxAdminPageController();
    }

    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "tg.module-call.tx", name = "admin-enabled", havingValue = "true", matchIfMissing = true)
    public RpcTxAdminController rpcTxAdminController(RpcTxCoordinator coordinator) {
        log.info("RPC tx admin API registered at {}", RpcTxAdminPaths.API);
        return new RpcTxAdminController(coordinator);
    }

    @Bean
    public MqRpcGateway mqRpcGateway(
            RabbitTemplate rabbitTemplate,
            ModuleCallProperties properties,
            ObjectProvider<RpcClientExceptionConverter> converters) {
        List<RpcClientExceptionConverter> resolved = new ArrayList<>();
        RpcClientExceptionConverter biz = OptionalBizExceptionRpcSupport.clientConverterIfPresent();
        if (biz != null) {
            resolved.add(biz);
        }
        converters.orderedStream().forEach(resolved::add);
        resolved.add(new RpcClientExceptionConverter.Default());
        return new MqRpcGateway(rabbitTemplate, properties, resolved);
    }

    @Bean
    public MqApiRpcDispatcher mqApiRpcDispatcher(
            ModuleApiProviderRegistry providerRegistry,
            ObjectProvider<RpcExceptionConverter> converters) {
        List<RpcExceptionConverter> resolved = new ArrayList<>();
        converters.orderedStream().forEach(resolved::add);
        RpcExceptionConverter biz = OptionalBizExceptionRpcSupport.serverConverterIfPresent();
        if (biz != null && resolved.stream().noneMatch(b -> b.getClass().equals(biz.getClass()))) {
            resolved.add(biz);
        }
        return new MqApiRpcDispatcher(providerRegistry, resolved);
    }

    @Bean
    public ModuleRpcEndpointRegistrar moduleRpcEndpointRegistrar(
            ModuleApiProviderRegistry providerRegistry,
            RabbitListenerEndpointRegistry endpointRegistry,
            SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory,
            AmqpAdmin amqpAdmin,
            MqApiRpcDispatcher dispatcher) {
        return new ModuleRpcEndpointRegistrar(
                providerRegistry, endpointRegistry, rabbitListenerContainerFactory, amqpAdmin, dispatcher);
    }
}
