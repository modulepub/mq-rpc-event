package pub.module.mqevent;

import com.github.fridujo.rabbitmq.mock.MockConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

/**
 * 未配置外部 RabbitMQ broker 时，自动启用进程内 Mock AMQP（供 Spring Cloud Stream 等使用）。
 * <p>ModuleRpc 场景由 {@code spring-boot-starter-mq-rpc} 的嵌入式配置优先处理。</p>
 */
@Slf4j
@AutoConfiguration(before = RabbitAutoConfiguration.class)
@ConditionalOnClass(MockConnectionFactory.class)
@ConditionalOnMissingBean(ConnectionFactory.class)
@Conditional(EmbeddedRabbitAutoConfiguration.OnNoExternalRabbitBrokerCondition.class)
public class EmbeddedRabbitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    MockConnectionFactory tgEmbeddedMockConnectionFactory() {
        return new MockConnectionFactory();
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(ConnectionFactory.class)
    CachingConnectionFactory rabbitConnectionFactory(MockConnectionFactory tgEmbeddedMockConnectionFactory) {
        log.info("未检测到 spring.rabbitmq 外部 broker 配置，已启用嵌入式 RabbitMQ（rabbitmq-mock）");
        return new CachingConnectionFactory(tgEmbeddedMockConnectionFactory);
    }

    static final class OnNoExternalRabbitBrokerCondition
            implements org.springframework.context.annotation.Condition {

        @Override
        public boolean matches(org.springframework.context.annotation.ConditionContext context,
                               org.springframework.core.type.AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            return !environment.containsProperty("spring.rabbitmq.host")
                    && !environment.containsProperty("spring.rabbitmq.addresses");
        }
    }
}
