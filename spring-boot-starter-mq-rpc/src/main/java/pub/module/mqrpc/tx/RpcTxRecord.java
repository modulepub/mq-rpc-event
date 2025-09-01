package pub.module.mqrpc.tx;

import lombok.Data;

import java.time.Instant;

/**
 * RPC 分布式事务分支持久化记录。
 */
@Data
public class RpcTxRecord {

    private String branchId;
    private String globalXid;
    private String module;
    private String service;
    private String method;
    private String compensateMethod;
    private String argsJson;
    private RpcTxStatus status;
    private String errorMessage;
    private int compensateAttempts;
    private Instant createdAt;
    private Instant updatedAt;
}
