package pub.module.mqrpc;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记跨模块同步 RPC 契约接口（Feign 等价物）。
 * <p>
 * module 路由键由包路径 {@code pub.module.{module}.api.service} 自动推导。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcApi {

    /**
     * 是否参与分布式事务（Saga：失败回滚 + 不一致时补偿）。默认开启。
     */
    boolean transactional() default true;
}
