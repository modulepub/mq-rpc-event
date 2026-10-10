package pub.module.mqevent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 {@code XxxConsumer} 嵌套子接口上的消费方槽位（group + Stream function 名）。
 * <p>
 * {@link #group()} / {@link #function()} 留空时由 {@link MqNamingSupport} 根据槽位接口路径与类名自动推导。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MqSubscribe {

    /** consumer group；空串表示自动推导（槽位包 module 或嵌套短名小写）。 */
    String group() default "";

    /** Spring Cloud Function 名；空串表示自动推导（槽位类名 lowerCamel 或 {@code {prefix}{RootBase}}）。 */
    String function() default "";
}
