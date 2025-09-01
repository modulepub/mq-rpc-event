package pub.module.mqrpc;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 RPC 契约中的补偿方法，与正向方法成对出现。
 * <p>
 * {@link #forMethod()} 为空时，约定补偿方法名为 {@code compensate + 首字母大写(正向方法名)}。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcCompensate {

    /**
     * 对应的正向 RPC 方法名。
     */
    String forMethod();
}
