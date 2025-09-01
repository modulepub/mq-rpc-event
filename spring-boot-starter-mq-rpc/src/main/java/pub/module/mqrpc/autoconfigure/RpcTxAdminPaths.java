package pub.module.mqrpc.autoconfigure;

/**
 * RPC 分布式事务管理页固定路径（位于 {@code /pub/**} 免登录区）。
 */
public final class RpcTxAdminPaths {

    public static final String BASE = "/pub/tg-rpc-tx";

    public static final String API = BASE + "/api";

    private RpcTxAdminPaths() {
    }
}
