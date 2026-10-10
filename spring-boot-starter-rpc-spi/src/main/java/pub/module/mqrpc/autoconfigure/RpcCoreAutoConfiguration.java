package pub.module.mqrpc.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import pub.module.mqrpc.autoconfigure.support.DefaultRpcExceptionConverter;
import pub.module.mqrpc.autoconfigure.support.OptionalBizExceptionRpcSupport;
import pub.module.mqrpc.spi.RpcExceptionConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * ModuleApi RPC 核心自动配置（契约扫描、本地 Provider、动态代理），与具体远程传输实现解耦。
 */
@AutoConfiguration
@ConditionalOnRpcApiPresent
@EnableConfigurationProperties(ModuleCallProperties.class)
@Import(ModuleApiClientRegistrar.class)
public class RpcCoreAutoConfiguration {

    @Bean(name = ModuleApiProxyCreator.BEAN_NAME)
    public ModuleApiProxyCreator moduleApiProxyCreator(org.springframework.context.ApplicationContext applicationContext) {
        return new ModuleApiProxyCreator(applicationContext);
    }

    @Bean
    public static ModuleApiProviderRegistry moduleApiProviderRegistry(
            ObjectProvider<RpcExceptionConverter> converters,
            ObjectProvider<PlatformTransactionManager> transactionManagers) {
        List<RpcExceptionConverter> resolved = new ArrayList<>();
        converters.orderedStream().forEach(resolved::add);
        if (resolved.stream().noneMatch(c -> c instanceof DefaultRpcExceptionConverter)) {
            resolved.add(new DefaultRpcExceptionConverter());
        }
        RpcExceptionConverter biz = OptionalBizExceptionRpcSupport.serverConverterIfPresent();
        if (biz != null) {
            resolved.add(biz);
        }
        return new ModuleApiProviderRegistry(resolved, transactionManagers.getIfAvailable());
    }

    @Bean
    static ModuleApiProviderBootstrap moduleApiProviderBootstrap(
            ConfigurableListableBeanFactory beanFactory,
            ModuleApiProviderRegistry providerRegistry) {
        return new ModuleApiProviderBootstrap(beanFactory, providerRegistry);
    }
}
