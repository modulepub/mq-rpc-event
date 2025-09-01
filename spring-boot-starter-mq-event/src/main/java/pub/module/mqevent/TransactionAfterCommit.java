package pub.module.mqevent;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 在 Spring 事务提交后执行副作用（MQ、外部通知等），避免回滚后仍投递消息。
 */
public final class TransactionAfterCommit {

    private TransactionAfterCommit() {
    }

    /**
     * 若当前存在活跃事务同步，则在 {@code afterCommit} 执行；否则立即执行。
     */
    public static void runAfterCommit(Runnable task) {
        if (task == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }
}
