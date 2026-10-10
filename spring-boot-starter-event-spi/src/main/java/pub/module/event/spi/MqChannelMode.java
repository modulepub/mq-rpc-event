package pub.module.event.spi;

/**
 * MQ 渠道模式。
 */
public enum MqChannelMode {

    /** 单向 fire-and-forget */
    FIRE_AND_FORGET,

    /** request-reply */
    REQUEST_REPLY
}
