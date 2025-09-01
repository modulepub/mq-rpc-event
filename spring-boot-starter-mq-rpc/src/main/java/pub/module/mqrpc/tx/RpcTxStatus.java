package pub.module.mqrpc.tx;

/**
 * RPC 分布式事务分支状态。
 */
public enum RpcTxStatus {

    /** 已发起，等待确认 */
    PENDING,

    /** 正向调用已成功 */
    CONFIRMED,

    /** 正向调用失败 */
    FAILED,

    /** 等待补偿（调用方本地事务已回滚或检测到不一致） */
    COMPENSATE_PENDING,

    /** 补偿已成功 */
    COMPENSATED,

    /** 补偿失败，需人工介入 */
    COMPENSATE_FAILED,

    /** 需人工处理（超时、应答丢失等） */
    NEEDS_MANUAL
}
