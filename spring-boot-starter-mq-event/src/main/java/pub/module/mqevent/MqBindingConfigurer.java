package pub.module.mqevent;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.core.Ordered;

/**
 * 在 {@link BindingServiceProperties} 初始化前写入 inbound binding，
 * 避免 BFPP 动态属性未绑定到 Stream 导致消费者未连上 Rabbit。
 */
class MqBindingConfigurer implements BeanPostProcessor, Ordered {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof BindingServiceProperties bindingServiceProperties) {
        for (MqSubscription subscription : MqConsumerRegistrar.registeredSubscriptions()) {
            MqChannelMetadata channel = subscription.channel();
            BindingProperties inbound = bindingServiceProperties.getBindingProperties(subscription.inBinding());
            inbound.setDestination(channel.destination());
            inbound.setGroup(subscription.group());

            BindingProperties outbound = bindingServiceProperties.getBindingProperties(channel.outBinding());
            outbound.setDestination(channel.destination());
        }
        }
        return bean;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
