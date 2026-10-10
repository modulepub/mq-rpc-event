package pub.module.rpc.spi;

import pub.module.rpc.spi.RpcResult;

/**
 * 将服务端异常转换为 {@link RpcResult}（可扩展以对接宿主项目的业务异常体系）。
 */
public interface RpcExceptionConverter {

    boolean supports(Throwable throwable);

    RpcResult toRpcResult(Throwable throwable);
}
