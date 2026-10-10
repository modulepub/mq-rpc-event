package pub.module.mqrpc.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import pub.module.mqrpc.RpcEnvelope;
import pub.module.mqrpc.RpcResult;
import pub.module.mqrpc.spi.RpcExceptionConverter;

import java.util.Collections;
import java.util.List;

/**
 * MQ RPC 提供方统一分发器（Feign Server 等价物）。
 */
@Slf4j
public class MqApiRpcDispatcher {

    private final ModuleApiProviderRegistry providerRegistry;
    private final List<RpcExceptionConverter> exceptionConverters;

    public MqApiRpcDispatcher(ModuleApiProviderRegistry providerRegistry,
                                List<RpcExceptionConverter> exceptionConverters) {
        this.providerRegistry = providerRegistry;
        this.exceptionConverters = exceptionConverters != null ? exceptionConverters : Collections.emptyList();
    }

    public RpcResult dispatch(RpcEnvelope envelope) {
        if (envelope == null) {
            return RpcResult.fail(IllegalArgumentException.class.getName(), "INVALID_ENVELOPE", "请求体为空");
        }
        try {
            return providerRegistry.dispatch(envelope);
        } catch (Exception ex) {
            for (RpcExceptionConverter converter : exceptionConverters) {
                if (converter.supports(ex)) {
                    return converter.toRpcResult(ex);
                }
            }
            log.error("MQ RPC 分发失败 module={} service={} method={}",
                    envelope.getModule(), envelope.getService(), envelope.getMethod(), ex);
            return RpcResult.fail(ex.getClass().getName(), "RPC_ERROR", ex.getMessage());
        }
    }
}
