package pub.module.mqrpc.autoconfigure;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;
import pub.module.mqrpc.RpcApi;

/**
 * Api 契约解析与 MQ 路由命名（{@link RpcApi} 标记 + 包路径自动推导 module）。
 */
public final class ModuleApiSupport {

    static final String MODULE_PACKAGE_PREFIX = "pub.module.";
    static final String API_SERVICE_MARKER = ".api.service.Api";
    static final String API_SERVICE_SUFFIX = "Service";
    static final String RPC_REQUEST_PREFIX = "api.rpc.";
    static final String RPC_REPLY_PREFIX = "api.rpc.reply.";

    private ModuleApiSupport() {
    }

    public static boolean isModuleApiInterface(Class<?> type) {
        if (type == null || !type.isInterface()) {
            return false;
        }
        if (AnnotatedElementUtils.findMergedAnnotation(type, RpcApi.class) == null) {
            return false;
        }
        String name = type.getName();
        return name.contains(API_SERVICE_MARKER) && type.getSimpleName().endsWith(API_SERVICE_SUFFIX);
    }

    public static String resolveModule(Class<?> apiInterface) {
        return validateModuleKey(resolveModuleFromClassName(apiInterface.getName()));
    }

    public static String resolveModuleFromServiceName(String serviceClassName) {
        if (!StringUtils.hasText(serviceClassName)) {
            throw new IllegalArgumentException("service 类名为空");
        }
        return validateModuleKey(resolveModuleFromClassName(serviceClassName));
    }

    public static String rpcRequestQueue(String module) {
        return RPC_REQUEST_PREFIX + validateModuleKey(module);
    }

    public static String rpcReplyQueue(String module) {
        return RPC_REPLY_PREFIX + validateModuleKey(module);
    }

    public static void assertModuleRouting(String declaredModule, Class<?> apiInterface) {
        String expected = resolveModule(apiInterface);
        if (!expected.equals(validateModuleKey(declaredModule))) {
            throw new IllegalArgumentException(
                    "RPC 模块路由不一致: envelope.module=" + declaredModule + ", 契约期望=" + expected
                            + ", 接口=" + apiInterface.getName());
        }
    }

    public static String providerBeanName(Class<?> apiInterface) {
        return "moduleApiProvider." + apiInterface.getName();
    }

    public static String proxyBeanName(Class<?> apiInterface) {
        return "moduleApiProxy." + apiInterface.getName();
    }

    public static String[] parameterTypeNames(Class<?>[] parameterTypes) {
        String[] names = new String[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            names[i] = parameterTypes[i].getName();
        }
        return names;
    }

    private static String resolveModuleFromClassName(String className) {
        if (className.startsWith(MODULE_PACKAGE_PREFIX) && className.contains(".api.")) {
            String rest = className.substring(MODULE_PACKAGE_PREFIX.length());
            int dot = rest.indexOf('.');
            if (dot > 0) {
                return rest.substring(0, dot);
            }
        }
        throw new IllegalArgumentException("无法从类路径推断 module 路由键: " + className
                + "（须匹配 " + MODULE_PACKAGE_PREFIX + "{module}.api...）");
    }

    private static String validateModuleKey(String module) {
        if (!StringUtils.hasText(module)) {
            throw new IllegalArgumentException("module 路由键不能为空");
        }
        String trimmed = module.trim();
        if (trimmed.contains(".") || trimmed.contains(" ") || trimmed.contains("/")) {
            throw new IllegalArgumentException("非法 module 路由键: " + module
                    + "（仅允许单段标识，如 system、order）");
        }
        return trimmed;
    }
}
