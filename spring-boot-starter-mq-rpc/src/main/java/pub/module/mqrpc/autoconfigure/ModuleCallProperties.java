package pub.module.mqrpc.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * 模块 RPC 配置：{@code tg.module-call.*}。
 */
@Data
@ConfigurationProperties(prefix = "tg.module-call")
public class ModuleCallProperties {

    /**
     * MQ Request-Reply 应答超时（毫秒）。
     */
    private long defaultTimeoutMs = 5000L;

    @NestedConfigurationProperty
    private final Tx tx = new Tx();

    @Data
    public static class Tx {

        /**
         * 是否启用 RPC 分布式事务（Saga + 补偿）。
         */
        private boolean enabled = true;

        /**
         * 是否启用事务异常管理页面与 REST API（{@link RpcTxAdminPaths#BASE}）。
         * 设为 {@code false} 时页面与补偿接口均不注册。
         */
        private boolean adminEnabled = true;
    }
}
