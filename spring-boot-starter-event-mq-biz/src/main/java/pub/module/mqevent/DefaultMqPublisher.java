package pub.module.mqevent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;

/**
 * 基于 {@link StreamBridge} 的 MQ 发布实现。
 */
@Slf4j
class DefaultMqPublisher implements MqPublisher {

    private final StreamBridge streamBridge;

    DefaultMqPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @Override
    public boolean publish(Class<?> consumerContract, Object payload) {
        return doPublish(consumerContract, payload);
    }

    @Override
    public boolean publishAfterCommit(Class<?> consumerContract, Object payload) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionAfterCommit.runAfterCommit(() -> doPublish(consumerContract, payload));
            return true;
        }
        return doPublish(consumerContract, payload);
    }

    private boolean doPublish(Class<?> consumerContract, Object payload) {
        MqChannelMetadata metadata = MqContractSupport.getChannelMetadata(consumerContract);
        String outBinding = metadata.outBinding();
        boolean sent = streamBridge.send(outBinding, payload);
        if (sent) {
            log.info("MQ 消息已发送 destination={} outBinding={}", metadata.destination(), outBinding);
        } else {
            log.error("MQ 消息发送失败 destination={} outBinding={}",
                    metadata.destination(), outBinding);
        }
        return sent;
    }
}
