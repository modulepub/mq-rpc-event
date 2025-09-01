package pub.module.mqrpc.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import pub.module.mqrpc.RpcApi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 在 Spring Boot 主应用包及其子包内扫描 {@link RpcApi} 契约接口。
 */
final class RpcApiClasspathScanner {

    private RpcApiClasspathScanner() {
    }

    static boolean hasRpcApiInterfaces(List<String> basePackages) {
        return hasRpcApiInterfaces(basePackages, resolveClassLoader(null));
    }

    static boolean hasRpcApiInterfaces(List<String> basePackages, ClassLoader classLoader) {
        return !findRpcApiInterfaceNames(basePackages, classLoader).isEmpty();
    }

    static Set<String> findRpcApiInterfaceNames(List<String> basePackages) {
        return findRpcApiInterfaceNames(basePackages, resolveClassLoader(null));
    }

    static Set<String> findRpcApiInterfaceNames(List<String> basePackages, ClassLoader classLoader) {
        Set<String> names = new LinkedHashSet<>();
        if (basePackages == null || basePackages.isEmpty()) {
            return names;
        }
        ClassLoader loader = resolveClassLoader(classLoader);
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(loader);
        MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(loader);
        for (String basePackage : basePackages) {
            if (!StringUtils.hasText(basePackage)) {
                continue;
            }
            String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
                    + ClassUtils.convertClassNameToResourcePath(basePackage.trim()) + "/**/*.class";
            try {
                for (Resource resource : resolver.getResources(packageSearchPath)) {
                    MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
                    if (!metadataReader.getClassMetadata().isInterface()) {
                        continue;
                    }
                    if (!metadataReader.getAnnotationMetadata().hasAnnotation(RpcApi.class.getName())) {
                        continue;
                    }
                    String className = metadataReader.getClassMetadata().getClassName();
                    if (isModuleApiClassName(className)) {
                        names.add(className);
                    }
                }
            } catch (IOException ignored) {
                // skip unreadable package
            }
        }
        return names;
    }

    static List<String> resolveScanPackages(org.springframework.beans.factory.ListableBeanFactory beanFactory) {
        Set<String> packages = new LinkedHashSet<>();
        if (beanFactory != null && AutoConfigurationPackages.has(beanFactory)) {
            packages.addAll(AutoConfigurationPackages.get(beanFactory));
        }
        // TG-boot 使用 scanBasePackages=pub.module，但 AutoConfigurationPackages 通常只有启动类包（如 pub.module.runner）
        packages.add("pub.module");
        return new ArrayList<>(packages);
    }

    private static ClassLoader resolveClassLoader(ClassLoader preferred) {
        if (preferred != null) {
            return preferred;
        }
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            return context;
        }
        return ClassUtils.getDefaultClassLoader();
    }

    private static boolean isModuleApiClassName(String className) {
        return StringUtils.hasText(className)
                && className.startsWith(ModuleApiSupport.MODULE_PACKAGE_PREFIX)
                && className.contains(ModuleApiSupport.API_SERVICE_MARKER)
                && className.endsWith(ModuleApiSupport.API_SERVICE_SUFFIX);
    }
}
