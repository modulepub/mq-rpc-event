package pub.module.mqevent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 MQ 根契约接口（定义在生产者 {@code *-api} 的 {@code XxxConsumer} 上）。
 * <p>
 * {@link #destination()} / {@link #producerFunction()} 留空时由 {@link MqNamingSupport} 根据包路径与 handler 方法名自动推导。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MqChannel {

    /**
     * Rabbit destination；空串表示自动推导（{@code {module}.{subject-kebab}.{action}}）。
     */
    String destination() default "";

    /**
     * Spring Cloud Function 生产者函数名；空串表示自动推导（{@code {module}{MethodBase}}）。
     */
    String producerFunction() default "";

    MqChannelMode mode() default MqChannelMode.FIRE_AND_FORGET;
}
