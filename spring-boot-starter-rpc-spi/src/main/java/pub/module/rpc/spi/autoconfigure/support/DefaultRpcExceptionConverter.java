package pub.module.rpc.spi.autoconfigure.support;

import pub.module.rpc.spi.RpcBusinessException;
import pub.module.rpc.spi.RpcResult;
import pub.module.rpc.spi.RpcExceptionConverter;

/**
 * 默认服务端异常转换。
 */
public class DefaultRpcExceptionConverter implements RpcExceptionConverter {

    @Override
    public boolean supports(Throwable throwable) {
        return throwable instanceof RpcBusinessException;
    }

    @Override
    public RpcResult toRpcResult(Throwable throwable) {
        RpcBusinessException ex = (RpcBusinessException) throwable;
        return RpcResult.fail(RpcBusinessException.class.getName(), ex.getErrorCode(), ex.getMessage());
    }
}
