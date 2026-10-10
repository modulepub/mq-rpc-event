package pub.module.rpc.spi.tx;

import pub.module.rpc.spi.RpcEnvelope;

/**
 * 客户端代理侧分布式事务协调（实现由 rpc-mq-biz 等传输模块提供）。
 */
public interface RpcTxClientCoordinator {

    boolean isEnabled();

    String ensureGlobalXid();

    void registerRollbackHook(String globalXid);

    String beginBranch(String globalXid, RpcEnvelope envelope, String compensateMethod);

    void confirm(String branchId);

    void fail(String branchId, String errorMessage);

    void markNeedsManual(String branchId, String errorMessage);
}
