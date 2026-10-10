package pub.module.mqrpc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * MQ RPC 应答载荷。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RpcResult implements Serializable {

    private boolean success;
    private Object data;
    private String errorCode;
    private String errorMessage;
    private String errorType;

    public static RpcResult ok(Object data) {
        return new RpcResult(true, data, null, null, null);
    }

    public static RpcResult fail(String errorType, String errorCode, String errorMessage) {
        return new RpcResult(false, null, errorCode, errorMessage, errorType);
    }
}
