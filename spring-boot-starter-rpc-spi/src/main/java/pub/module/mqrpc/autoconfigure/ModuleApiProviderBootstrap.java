package pub.module.mqrpc.autoconfigure;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;

/**
 * 所有单例 Bean 就绪后扫描并注册 ModuleApi Provider，避免 BeanPostProcessor 过早初始化。
 */
class ModuleApiProviderBootstrap implements SmartInitializingSingleton, Ordered {

    private final ConfigurableListableBeanFactory beanFactory;
    private final ModuleApiProviderRegistry providerRegistry;

    ModuleApiProviderBootstrap(ConfigurableListableBeanFactory beanFactory,
                               ModuleApiProviderRegistry providerRegistry) {
        this.beanFactory = beanFactory;
        this.providerRegistry = providerRegistry;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            if (beanName.startsWith("moduleApiProxy.")) {
                continue;
            }
            if (!beanFactory.isSingleton(beanName) || !beanFactory.containsSingleton(beanName)) {
                continue;
            }
            Object bean = beanFactory.getSingleton(beanName);
            if (bean == null || java.lang.reflect.Proxy.isProxyClass(bean.getClass())) {
                continue;
            }
            providerRegistry.registerProviderBean(bean);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
