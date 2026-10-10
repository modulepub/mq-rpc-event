package pub.module.mqevent;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.catalog.BeanFactoryAwareFunctionRegistry;
import org.springframework.core.Ordered;

/**
 * 在 {@code FunctionCatalog} 初始化完成后、Stream 首次 {@code lookup} 之前，
 * 将 MQ Consumer 预注册进 FunctionCatalog 内部表，避免 SCF 4.3 首次 lookup 的 WARN。
 */
class MqFunctionRegistryInitializer implements BeanPostProcessor, Ordered {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof BeanFactoryAwareFunctionRegistry registry) {
            for (FunctionRegistration<?> registration : MqConsumerRegistrar.drainRegistrations()) {
                registry.register(registration);
            }
        }
        return bean;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
