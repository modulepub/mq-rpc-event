package pub.module.rpc.spi;

import pub.module.rpc.spi.RpcEnvelope;
import pub.module.rpc.spi.RpcResult;

/**
 * 同步 RPC 远程传输插件（MQ、gRPC 等实现此接口并注册为 Spring Bean）。
 */
public interface RpcRemoteTransport {

    /** 实现标识，如 {@code mq}、{@code grpc}。 */
    String transportId();

    RpcResult request(RpcEnvelope envelope);

    void throwIfFailed(RpcResult result) throws Throwable;
}
