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
import org.springframework.context.annotation.Bean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import pub.module.rpc.spi.autoconfigure.ConditionalOnRpcApiPresent;
import pub.module.rpc.spi.autoconfigure.ModuleApiProviderRegistry;
import pub.module.rpc.spi.autoconfigure.ModuleCallProperties;
import pub.module.rpc.spi.autoconfigure.RpcCoreAutoConfiguration;
import pub.module.rpc.spi.autoconfigure.support.OptionalBizExceptionRpcSupport;
import pub.module.rpc.spi.RpcClientExceptionConverter;
import pub.module.rpc.spi.RpcExceptionConverter;
import pub.module.rpc.spi.RpcRemoteTransport;
import pub.module.mqrpc.tx.InMemoryRpcTxRecordRepository;
import pub.module.mqrpc.tx.RpcTxCompensator;
import pub.module.mqrpc.tx.RpcTxCoordinator;
import pub.module.rpc.spi.tx.RpcTxRecordRepository;
import tg.rpc.mqrpc.admin.RpcTxAdminController;
import tg.rpc.mqrpc.admin.RpcTxAdminPageController;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * ModuleApi MQ 远程传输、事务协调与 Rabbit 端点注册。
 */
@AutoConfiguration(after = {EmbeddedRabbitAutoConfiguration.class, RpcCoreAutoConfiguration.class})
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnRpcApiPresent
@EnableConfigurationProperties(ModuleCallProperties.class)
@Slf4j
public class MqModuleRpcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RpcTxRecordRepository.class)
    public RpcTxRecordRepository rpcTxRecordRepository() {
        return new InMemoryRpcTxRecordRepository();
    }

    @Bean
    @Primary
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
    public RpcTxCompensator rpcTxCompensator(
            ModuleApiProviderRegistry providerRegistry,
            RpcRemoteTransport remoteTransport,
            ObjectProvider<ObjectMapper> objectMapper) {
        return new RpcTxCompensator(providerRegistry, remoteTransport, objectMapper.getIfAvailable());
    }

    @Bean
    public RpcTxCoordinator rpcTxCoordinator(
            ModuleCallProperties properties,
            RpcTxRecordRepository repository,
            RpcTxCompensator compensator,
            ObjectProvider<ObjectMapper> objectMapper) {
        ObjectMapper mapper = objectMapper.getIfAvailable();
        if (mapper == null) {
            mapper = JsonMapper.builder().findAndAddModules().build();
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
