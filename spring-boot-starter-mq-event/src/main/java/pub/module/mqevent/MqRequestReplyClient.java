package pub.module.mqevent;

import cn.hutool.core.lang.Assert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * request-reply 客户端：契约与应答槽位均来自生产者 {@code XxxConsumer}。
 */
@Slf4j
public class MqRequestReplyClient {

    private final RabbitTemplate rabbitTemplate;

    public MqRequestReplyClient(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 向指定契约的应答槽位发起 request-reply。
     *
     * @param consumerContract 根契约接口
     * @param responderSlot    嵌套槽位子接口（带 {@link MqSubscribe}）
     */
    @SuppressWarnings("unchecked")
    public <Q, R> R request(
            Class<?> consumerContract,
            Class<?> responderSlot,
            Q payload,
            Class<R> responseType,
            long timeoutMs) {
        Assert.notNull(consumerContract, "consumerContract 不能为空");
        Assert.notNull(responderSlot, "responderSlot 不能为空");
        Assert.notNull(payload, "payload 不能为空");
        MqContractSupport.assertSlotOfContract(consumerContract, responderSlot);
        MqChannelMetadata metadata = MqContractSupport.getChannelMetadata(consumerContract);
        if (metadata.mode() != MqChannelMode.REQUEST_REPLY) {
            throw new IllegalArgumentException("契约非 request-reply 模式: " + consumerContract.getName());
        }
        MqSubscribe slot = MqContractSupport.getSlotAnnotation(responderSlot);
        String targetQueue = MessagingQueueNames.consumerQueue(metadata.destination(), slot.group());
        rabbitTemplate.setReplyTimeout(timeoutMs);
        Object raw = rabbitTemplate.convertSendAndReceive(targetQueue, payload);
        if (raw == null) {
            return null;
        }
        if (responseType.isInstance(raw)) {
            return (R) raw;
        }
        log.warn("request-reply 应答类型不匹配 destination={} expected={} actual={}",
                metadata.destination(), responseType.getName(), raw.getClass().getName());
        return null;
    }
}
