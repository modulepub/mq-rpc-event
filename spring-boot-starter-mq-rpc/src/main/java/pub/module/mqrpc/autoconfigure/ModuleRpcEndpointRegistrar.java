package pub.module.mqrpc.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.listener.MethodRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.Ordered;
import org.springframework.util.ReflectionUtils;
import pub.module.mqrpc.RpcEnvelope;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 按本 JVM 已注册的 Provider 模块，自动声明并监听 {@code api.rpc.{module}} 请求队列。
 */
@Slf4j
public class ModuleRpcEndpointRegistrar implements SmartInitializingSingleton, Ordered {

    private final ModuleApiProviderRegistry providerRegistry;
    private final RabbitListenerEndpointRegistry endpointRegistry;
    private final SimpleRabbitListenerContainerFactory listenerContainerFactory;
    private final AmqpAdmin amqpAdmin;
    private final MqApiRpcDispatcher dispatcher;

    public ModuleRpcEndpointRegistrar(
            ModuleApiProviderRegistry providerRegistry,
            RabbitListenerEndpointRegistry endpointRegistry,
            SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory,
            AmqpAdmin amqpAdmin,
            MqApiRpcDispatcher dispatcher) {
        this.providerRegistry = providerRegistry;
        this.endpointRegistry = endpointRegistry;
        this.listenerContainerFactory = rabbitListenerContainerFactory;
        this.amqpAdmin = amqpAdmin;
        this.dispatcher = dispatcher;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Method dispatchMethod = ReflectionUtils.findMethod(MqApiRpcDispatcher.class, "dispatch", RpcEnvelope.class);
        if (dispatchMethod == null) {
            throw new IllegalStateException("未找到 MqApiRpcDispatcher.dispatch 方法");
        }
        List<String> queues = new ArrayList<>();
        for (String module : providerRegistry.getProviderModules()) {
            String queueName = ModuleApiSupport.rpcRequestQueue(module);
            Queue queue = new Queue(queueName, true);
            amqpAdmin.declareQueue(queue);
            MethodRabbitListenerEndpoint endpoint = new MethodRabbitListenerEndpoint();
            endpoint.setId("tg-module-rpc-" + module);
            endpoint.setAdmin(amqpAdmin);
            endpoint.setQueues(queue);
            endpoint.setBean(dispatcher);
            endpoint.setMethod(dispatchMethod);
            endpoint.setMessageHandlerMethodFactory(ModuleRpcListenerSupport.messageHandlerMethodFactory());
            endpointRegistry.registerListenerContainer(endpoint, listenerContainerFactory, true);
            queues.add(queueName);
        }
        if (queues.isEmpty()) {
            log.info("ModuleRpc 无 Provider 模块，跳过 RPC 队列监听");
            return;
        }
        log.info("ModuleRpc 自动监听请求队列（由 Api 接口路径推导 module）: {}", queues);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
