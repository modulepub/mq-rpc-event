package pub.module.mqevent;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * MQ 消息发布门面。
 */
public interface MqPublisher {

    /**
     * @return {@code true} 表示 StreamBridge 已成功投递
     */
    boolean publish(Class<?> consumerContract, Object payload);

    /**
     * 在事务提交后发布；无活跃事务时与 {@link #publish} 等价（立即投递）。
     *
     * @return 无活跃事务时为 {@link #publish} 结果；有事务时为 {@code true}（已登记提交后投递）
     */
    default boolean publishAfterCommit(Class<?> consumerContract, Object payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionAfterCommit.runAfterCommit(() -> publish(consumerContract, payload));
            return true;
        }
        return publish(consumerContract, payload);
    }
}
