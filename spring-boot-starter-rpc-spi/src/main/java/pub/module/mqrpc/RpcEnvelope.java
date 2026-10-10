package pub.module.mqrpc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * MQ RPC 请求载荷。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RpcEnvelope implements Serializable {

    private String module;
    private String service;
    private String method;
    private String[] parameterTypes;
    private Object[] args;
    private String traceId;
    /** 分布式事务全局 ID */
    private String txXid;
    /** Saga 分支 ID */
    private String branchId;
    /** 是否为补偿调用 */
    private boolean compensateInvocation;
}
