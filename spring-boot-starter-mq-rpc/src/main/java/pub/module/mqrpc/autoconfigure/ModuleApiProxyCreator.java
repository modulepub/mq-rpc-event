package pub.module.mqrpc.autoconfigure;

import org.springframework.context.ApplicationContext;
import pub.module.mqrpc.tx.RpcTxCoordinator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按需创建并缓存 ModuleApi JDK 动态代理，避免为每个契约注册 {@link org.springframework.beans.factory.FactoryBean}。
 */
public class ModuleApiProxyCreator {

    public static final String BEAN_NAME = "moduleApiProxyCreator";

    private final ApplicationContext applicationContext;
    private final Map<Class<?>, Object> proxies = new ConcurrentHashMap<>();

    public ModuleApiProxyCreator(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public Object createProxy(Class<?> apiInterface) {
        return proxies.computeIfAbsent(apiInterface, this::newProxy);
    }

    private Object newProxy(Class<?> apiInterface) {
        ModuleApiProxyInvocationHandler handler = new ModuleApiProxyInvocationHandler(
                apiInterface,
                applicationContext.getBean(ModuleApiProviderRegistry.class),
                applicationContext.getBean(MqRpcGateway.class),
                applicationContext.getBean(RpcTxCoordinator.class));
        return java.lang.reflect.Proxy.newProxyInstance(
                apiInterface.getClassLoader(),
                new Class<?>[] {apiInterface},
                handler);
    }
}
