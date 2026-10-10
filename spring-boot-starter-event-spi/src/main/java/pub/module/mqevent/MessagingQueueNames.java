package pub.module.mqevent;

/**
 * Rabbit 队列命名规则（Spring Cloud Stream consumer group 约定）。
 */
public final class MessagingQueueNames {

    private MessagingQueueNames() {
    }

    /** 消费方队列名：{@code {destination}.{group}} */
    public static String consumerQueue(String destination, String group) {
        return destination + "." + group;
    }

    public static String outBinding(String producerFunction) {
        return producerFunction + "-out-0";
    }

    public static String inBinding(String function) {
        return function + "-in-0";
    }
}
