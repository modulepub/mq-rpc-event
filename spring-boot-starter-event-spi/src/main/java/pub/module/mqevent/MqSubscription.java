package pub.module.mqevent;

/**
 * 消费方槽位订阅信息（嵌套子接口 + 实现类）。
 */
public record MqSubscription(
        MqChannelMetadata channel,
        Class<?> slotInterface,
        String group,
        String function,
        String handlerClassName,
        String handlerBeanName) {

    public String inBinding() {
        return MessagingQueueNames.inBinding(function);
    }

    public boolean requestReply() {
        return channel.mode() == MqChannelMode.REQUEST_REPLY;
    }
}
