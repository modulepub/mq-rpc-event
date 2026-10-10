package pub.module.mqrpc.autoconfigure.support;

import pub.module.mqrpc.RpcResult;
import pub.module.mqrpc.spi.RpcClientExceptionConverter;
import pub.module.mqrpc.spi.RpcExceptionConverter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * 可选集成：当 classpath 存在 TG-boot {@code BizException} 时自动启用。
 */
public final class OptionalBizExceptionRpcSupport {

    private static final String BIZ_EXCEPTION = "pub.module.common.exception.BizException";
    private static final String BASE_ENUM = "pub.module.common.enums.BaseEnum";

    private OptionalBizExceptionRpcSupport() {
    }

    public static RpcExceptionConverter serverConverterIfPresent() {
        if (!isPresent(BIZ_EXCEPTION)) {
            return null;
        }
        return new BizExceptionServerConverter();
    }

    public static RpcClientExceptionConverter clientConverterIfPresent() {
        if (!isPresent(BIZ_EXCEPTION)) {
            return null;
        }
        return new BizExceptionClientConverter();
    }

    private static boolean isPresent(String className) {
        try {
            Class.forName(className, false, OptionalBizExceptionRpcSupport.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    private static final class BizExceptionServerConverter implements RpcExceptionConverter {

        @Override
        public boolean supports(Throwable throwable) {
            return BIZ_EXCEPTION.equals(throwable.getClass().getName());
        }

        @Override
        public RpcResult toRpcResult(Throwable throwable) {
            try {
                Method getEnum = throwable.getClass().getMethod("getErrorCodeEnum");
                Object errorCodeEnum = getEnum.invoke(throwable);
                String code = "BIZ_ERROR";
                if (errorCodeEnum != null) {
                    Method getCode = errorCodeEnum.getClass().getMethod("getCode");
                    Object codeValue = getCode.invoke(errorCodeEnum);
                    if (codeValue != null) {
                        code = codeValue.toString();
                    }
                }
                return RpcResult.fail(BIZ_EXCEPTION, code, throwable.getMessage());
            } catch (ReflectiveOperationException ex) {
                return RpcResult.fail(BIZ_EXCEPTION, "BIZ_ERROR", throwable.getMessage());
            }
        }
    }

    private static final class BizExceptionClientConverter implements RpcClientExceptionConverter {

        @Override
        public boolean supports(RpcResult result) {
            return result != null && !result.isSuccess() && BIZ_EXCEPTION.equals(result.getErrorType());
        }

        @Override
        public RuntimeException toException(RpcResult result) {
            try {
                Class<?> bizExceptionClass = Class.forName(BIZ_EXCEPTION);
                Class<?> baseEnumClass = Class.forName(BASE_ENUM);
                Object errorCodeEnum = java.lang.reflect.Proxy.newProxyInstance(
                        baseEnumClass.getClassLoader(),
                        new Class<?>[] {baseEnumClass},
                        (proxy, method, args) -> {
                            if ("getCode".equals(method.getName())) {
                                return result.getErrorCode() != null ? result.getErrorCode() : "BIZ_ERROR";
                            }
                            if ("getDesc".equals(method.getName())) {
                                return result.getErrorMessage() != null ? result.getErrorMessage() : "业务异常";
                            }
                            return null;
                        });
                Constructor<?> ctor = bizExceptionClass.getConstructor(baseEnumClass);
                return (RuntimeException) ctor.newInstance(errorCodeEnum);
            } catch (ReflectiveOperationException ex) {
                return new IllegalStateException(result.getErrorMessage(), ex);
            }
        }
    }
}
