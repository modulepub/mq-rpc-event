package pub.module.mqevent;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.cloud.stream.config.BindingServiceConfiguration;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;

/**
 * MQ 契约驱动自动配置：注册消费 Function、发布与 request-reply 客户端。
 */
@AutoConfiguration
@ConditionalOnClass(StreamBridge.class)
@AutoConfigureBefore({
        ContextFunctionCatalogAutoConfiguration.class,
        BindingServiceConfiguration.class
})
public class MqMessagingAutoConfiguration {

    @Bean
    public static MqConsumerRegistrar mqConsumerRegistrar() {
        return new MqConsumerRegistrar();
    }

    @Bean
    public static MqFunctionRegistryInitializer mqFunctionRegistryInitializer() {
        return new MqFunctionRegistryInitializer();
    }

    @Bean
    public static MqBindingConfigurer mqBindingConfigurer() {
        return new MqBindingConfigurer();
    }

    @Bean
    public MqPublisher mqPublisher(StreamBridge streamBridge) {
        return new DefaultMqPublisher(streamBridge);
    }

    @Bean
    @ConditionalOnClass(RabbitTemplate.class)
    public MqRequestReplyClient mqRequestReplyClient(RabbitTemplate rabbitTemplate) {
        return new MqRequestReplyClient(rabbitTemplate);
    }
}
