package pub.module.mqrpc;

/**
 * RPC 业务异常（客户端从 {@link RpcResult} 还原时抛出）。
 */
public class RpcBusinessException extends RuntimeException {

    private final String errorCode;

    public RpcBusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
