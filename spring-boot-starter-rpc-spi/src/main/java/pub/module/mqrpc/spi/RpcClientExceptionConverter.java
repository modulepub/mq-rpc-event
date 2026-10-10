package pub.module.mqrpc.spi;

import pub.module.mqrpc.RpcBusinessException;
import pub.module.mqrpc.RpcResult;

/**
 * 将失败的 {@link RpcResult} 还原为客户端异常。
 */
public interface RpcClientExceptionConverter {

    boolean supports(RpcResult result);

    RuntimeException toException(RpcResult result);

    /**
     * 默认：{@link RpcBusinessException} → 原样抛出，其余 → {@link IllegalStateException}。
     */
    final class Default implements RpcClientExceptionConverter {

        @Override
        public boolean supports(RpcResult result) {
            return result != null && !result.isSuccess();
        }

        @Override
        public RuntimeException toException(RpcResult result) {
            if (RpcBusinessException.class.getName().equals(result.getErrorType())) {
                return new RpcBusinessException(result.getErrorCode(), result.getErrorMessage());
            }
            String message = result.getErrorMessage() != null ? result.getErrorMessage() : "MQ RPC 调用失败";
            return new IllegalStateException(message);
        }
    }
}
