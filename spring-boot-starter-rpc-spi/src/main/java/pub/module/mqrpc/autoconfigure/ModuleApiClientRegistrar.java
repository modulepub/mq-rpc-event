package pub.module.mqrpc.autoconfigure;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.ResolvableType;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

import java.util.List;
import java.util.Set;

/**
 * 扫描主应用包下带 {@link pub.module.mqrpc.RpcApi} 的 Api*Service 契约并注册动态代理 Bean。
 */
public class ModuleApiClientRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry) {
        ClassLoader classLoader = resolveClassLoader();
        List<String> scanPackages = List.of("pub.module");
        Set<String> apiInterfaces = RpcApiClasspathScanner.findRpcApiInterfaceNames(scanPackages, classLoader);
        if (apiInterfaces.isEmpty()) {
            return;
        }
        for (String className : apiInterfaces) {
            registerProxy(registry, className, classLoader);
        }
    }

    private void registerProxy(BeanDefinitionRegistry registry, String className, ClassLoader classLoader) {
        try {
            Class<?> apiInterface = ClassUtils.forName(className, classLoader);
            if (!ModuleApiSupport.isModuleApiInterface(apiInterface)) {
                return;
            }
            String beanName = ModuleApiSupport.proxyBeanName(apiInterface);
            if (registry.containsBeanDefinition(beanName)) {
                return;
            }
            RootBeanDefinition definition = new RootBeanDefinition();
            definition.setFactoryBeanName(ModuleApiProxyCreator.BEAN_NAME);
            definition.setFactoryMethodName("createProxy");
            definition.getConstructorArgumentValues().addGenericArgumentValue(apiInterface);
            definition.setPrimary(true);
            definition.setAutowireCandidate(true);
            definition.setLazyInit(true);
            definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
            definition.setTargetType(ResolvableType.forClass(apiInterface));
            definition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, apiInterface.getName());
            registry.registerBeanDefinition(beanName, definition);
        } catch (ClassNotFoundException ignored) {
            // skip
        }
    }

    private static ClassLoader resolveClassLoader() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            return context;
        }
        return ClassUtils.getDefaultClassLoader();
    }
}
