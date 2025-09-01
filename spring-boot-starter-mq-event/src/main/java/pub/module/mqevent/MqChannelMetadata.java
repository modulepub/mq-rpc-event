package pub.module.mqevent;

import java.lang.reflect.Method;

/**
 * 解析后的 MQ 根渠道元数据。
 */
public record MqChannelMetadata(
        Class<?> contractInterface,
        String destination,
        String producerFunction,
        MqChannelMode mode,
        Method handlerMethod,
        Class<?> payloadType,
        Class<?> replyType) {

    public String outBinding() {
        return MessagingQueueNames.outBinding(producerFunction);
    }
}
