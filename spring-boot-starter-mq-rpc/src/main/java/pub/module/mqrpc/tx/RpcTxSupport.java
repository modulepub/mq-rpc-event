package pub.module.mqrpc.tx;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;
import pub.module.mqrpc.RpcApi;
import pub.module.mqrpc.RpcCompensate;

import java.lang.reflect.Method;
import java.util.Locale;

public final class RpcTxSupport {

    private RpcTxSupport() {
    }

    public static boolean isTransactional(Class<?> apiInterface) {
        RpcApi rpcApi = AnnotatedElementUtils.findMergedAnnotation(apiInterface, RpcApi.class);
        return rpcApi == null || rpcApi.transactional();
    }

    public static String resolveCompensateMethod(Class<?> apiInterface, String forwardMethod) {
        for (Method method : apiInterface.getMethods()) {
            RpcCompensate compensate = AnnotatedElementUtils.findMergedAnnotation(method, RpcCompensate.class);
            if (compensate != null && forwardMethod.equals(compensate.forMethod())) {
                return method.getName();
            }
        }
        if (forwardMethod == null || forwardMethod.isEmpty()) {
            throw new IllegalArgumentException("正向方法名为空，无法推导补偿方法");
        }
        return "compensate" + capitalize(forwardMethod);
    }

    public static boolean isCompensateMethod(Class<?> apiInterface, String methodName) {
        for (Method method : apiInterface.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            if (AnnotatedElementUtils.findMergedAnnotation(method, RpcCompensate.class) != null) {
                return true;
            }
        }
        return methodName != null && methodName.startsWith("compensate")
                && methodName.length() > "compensate".length();
    }

    private static String capitalize(String name) {
        if (!StringUtils.hasText(name)) {
            return name;
        }
        if (name.length() == 1) {
            return name.toUpperCase(Locale.ROOT);
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
