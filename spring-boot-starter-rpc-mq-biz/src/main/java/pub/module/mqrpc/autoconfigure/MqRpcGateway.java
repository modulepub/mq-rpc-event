package pub.module.mqrpc.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import pub.module.rpc.spi.RpcEnvelope;
import pub.module.rpc.spi.RpcResult;
import pub.module.rpc.spi.RpcClientExceptionConverter;
import pub.module.rpc.spi.RpcRemoteTransport;
import pub.module.rpc.spi.autoconfigure.ModuleApiSupport;
import pub.module.rpc.spi.autoconfigure.ModuleCallProperties;

import java.util.Collections;
import java.util.List;

/**
 * MQ Request-Reply RPC 网关（Feign Client 底层）。
 */
@Slf4j
public class MqRpcGateway implements RpcRemoteTransport {

    @Override
    public String transportId() {
        return "mq";
    }

    private final RabbitTemplate rabbitTemplate;
    private final ModuleCallProperties properties;
    private final List<RpcClientExceptionConverter> clientExceptionConverters;

    public MqRpcGateway(RabbitTemplate rabbitTemplate,
                          ModuleCallProperties properties,
                          List<RpcClientExceptionConverter> clientExceptionConverters) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.clientExceptionConverters = clientExceptionConverters != null
                ? clientExceptionConverters
                : Collections.emptyList();
    }

    @Override
    public RpcResult request(RpcEnvelope envelope) {
        Class<?> apiInterface = loadApiInterface(envelope.getService());
        ModuleApiSupport.assertModuleRouting(envelope.getModule(), apiInterface);
        String requestQueue = ModuleApiSupport.rpcRequestQueue(envelope.getModule());
        log.info("ModuleRpc [MQ] queue={} module={} service={} method={}",
                requestQueue,
                envelope.getModule(),
                envelope.getService(),
                envelope.getMethod());
        rabbitTemplate.setReplyTimeout(properties.getDefaultTimeoutMs());
        Object raw = rabbitTemplate.convertSendAndReceive(requestQueue, envelope);
        if (raw == null) {
            log.warn("ModuleRpc [MQ] 超时 queue={} service={} method={}",
                    requestQueue, envelope.getService(), envelope.getMethod());
            return RpcResult.fail(IllegalStateException.class.getName(), "RPC_TIMEOUT", "MQ RPC 应答超时");
        }
        if (raw instanceof RpcResult result) {
            return result;
        }
        log.warn("ModuleRpc [MQ] 应答类型不匹配 queue={} actual={}", requestQueue, raw.getClass().getName());
        return RpcResult.fail(IllegalStateException.class.getName(), "RPC_TYPE_MISMATCH", "应答类型不匹配");
    }

    @Override
    public void throwIfFailed(RpcResult result) {
        if (result == null || result.isSuccess()) {
            return;
        }
        for (RpcClientExceptionConverter converter : clientExceptionConverters) {
            if (converter.supports(result)) {
                throw converter.toException(result);
            }
        }
        throw new RpcClientExceptionConverter.Default().toException(result);
    }

    private static Class<?> loadApiInterface(String serviceClassName) {
        try {
            return Class.forName(serviceClassName);
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException("Api 接口不存在: " + serviceClassName, ex);
        }
    }
}
