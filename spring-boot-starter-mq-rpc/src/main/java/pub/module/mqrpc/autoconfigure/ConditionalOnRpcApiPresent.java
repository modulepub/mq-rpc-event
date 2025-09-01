package pub.module.mqrpc.autoconfigure;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConfigurationCondition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 仅当 classpath 中存在符合约定的 {@link pub.module.mqrpc.RpcApi} 契约接口时启用。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ConditionalOnRpcApiPresent.OnRpcApiPresentCondition.class)
@interface ConditionalOnRpcApiPresent {

    class OnRpcApiPresentCondition implements ConfigurationCondition {

        @Override
        public ConfigurationPhase getConfigurationPhase() {
            return ConfigurationPhase.PARSE_CONFIGURATION;
        }

        @Override
        public boolean matches(org.springframework.context.annotation.ConditionContext context,
                               org.springframework.core.type.AnnotatedTypeMetadata metadata) {
            var packages = RpcApiClasspathScanner.resolveScanPackages(context.getBeanFactory());
            ClassLoader classLoader = context.getClassLoader();
            if (classLoader == null) {
                classLoader = Thread.currentThread().getContextClassLoader();
            }
            boolean present = RpcApiClasspathScanner.hasRpcApiInterfaces(packages, classLoader);
            if (context.getEnvironment() != null
                    && context.getEnvironment().getProperty("tg.module-call.debug-rpc-api-scan", Boolean.class, false)) {
                var found = RpcApiClasspathScanner.findRpcApiInterfaceNames(packages, classLoader);
                org.slf4j.LoggerFactory.getLogger(ConditionalOnRpcApiPresent.class)
                        .info("@RpcApi scan packages={}, found={}, enabled={}", packages, found, present);
            }
            return present;
        }
    }
}
