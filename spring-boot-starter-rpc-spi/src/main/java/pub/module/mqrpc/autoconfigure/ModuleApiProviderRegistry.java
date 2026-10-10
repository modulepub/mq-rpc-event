package pub.module.mqrpc.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pub.module.mqrpc.RpcEnvelope;
import pub.module.mqrpc.RpcResult;
import pub.module.mqrpc.spi.RpcExceptionConverter;
import pub.module.mqrpc.tx.RpcTxSupport;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本 JVM 内 ModuleApi 提供方注册表（Api*ServiceImpl）。
 */
@Slf4j
public class ModuleApiProviderRegistry {

    private final List<RpcExceptionConverter> exceptionConverters;
    private final TransactionTemplate transactionTemplate;

    private final Map<Class<?>, Object> providers = new ConcurrentHashMap<>();
    private final Set<String> providerModules = ConcurrentHashMap.newKeySet();

    public ModuleApiProviderRegistry(List<RpcExceptionConverter> exceptionConverters,
                                     PlatformTransactionManager transactionManager) {
        this.exceptionConverters = exceptionConverters != null ? exceptionConverters : Collections.emptyList();
        this.transactionTemplate = transactionManager != null ? new TransactionTemplate(transactionManager) : null;
    }

    void registerProviderBean(Object bean) {
        Class<?> userClass = org.springframework.aop.support.AopUtils.getTargetClass(bean);
        for (Class<?> iface : userClass.getInterfaces()) {
            if (!ModuleApiSupport.isModuleApiInterface(iface)) {
                continue;
            }
            providers.put(iface, bean);
            providerModules.add(ModuleApiSupport.resolveModule(iface));
        }
    }

    public Set<String> getProviderModules() {
        return Collections.unmodifiableSet(providerModules);
    }

    public boolean hasProvider(Class<?> apiInterface) {
        return providers.containsKey(apiInterface);
    }

    public Object getProvider(Class<?> apiInterface) {
        Object provider = providers.get(apiInterface);
        if (provider == null) {
            throw new IllegalStateException("未找到 ModuleApi Provider: " + apiInterface.getName());
        }
        return provider;
    }

    public Object invoke(Class<?> apiInterface, Method method, Object[] args) throws ReflectiveOperationException {
        return method.invoke(getProvider(apiInterface), args);
    }

    public RpcResult dispatch(RpcEnvelope envelope) {
        try {
            Class<?> apiInterface = Class.forName(envelope.getService());
            if (!ModuleApiSupport.isModuleApiInterface(apiInterface)) {
                return RpcResult.fail(IllegalArgumentException.class.getName(), "INVALID_API",
                        "非 ModuleApi 接口: " + envelope.getService());
            }
            ModuleApiSupport.assertModuleRouting(envelope.getModule(), apiInterface);
            Method method = resolveMethod(apiInterface, envelope);
            Object[] args = envelope.getArgs() != null ? envelope.getArgs() : new Object[0];
            if (shouldRunInTransaction(apiInterface, envelope)) {
                return transactionTemplate.execute(status -> {
                    try {
                        return RpcResult.ok(invoke(apiInterface, method, args));
                    } catch (ReflectiveOperationException ex) {
                        status.setRollbackOnly();
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        return convertException(cause);
                    } catch (Exception ex) {
                        status.setRollbackOnly();
                        return convertException(ex);
                    }
                });
            }
            Object data = invoke(apiInterface, method, args);
            return RpcResult.ok(data);
        } catch (IllegalArgumentException ex) {
            return RpcResult.fail(ex.getClass().getName(), "ROUTING_ERROR", ex.getMessage());
        } catch (ReflectiveOperationException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            return convertException(cause);
        } catch (Exception ex) {
            return convertException(ex);
        }
    }

    private RpcResult convertException(Throwable ex) {
        for (RpcExceptionConverter converter : exceptionConverters) {
            if (converter.supports(ex)) {
                return converter.toRpcResult(ex);
            }
        }
        return RpcResult.fail(ex.getClass().getName(), "DISPATCH_ERROR", ex.getMessage());
    }

    private boolean shouldRunInTransaction(Class<?> apiInterface, RpcEnvelope envelope) {
        if (transactionTemplate == null) {
            return false;
        }
        if (envelope.isCompensateInvocation()) {
            return false;
        }
        return envelope.getTxXid() != null && RpcTxSupport.isTransactional(apiInterface);
    }

    private static Method resolveMethod(Class<?> apiInterface, RpcEnvelope envelope) throws ReflectiveOperationException {
        Class<?>[] paramTypes = new Class<?>[envelope.getParameterTypes().length];
        for (int i = 0; i < envelope.getParameterTypes().length; i++) {
            paramTypes[i] = Class.forName(envelope.getParameterTypes()[i]);
        }
        return apiInterface.getMethod(envelope.getMethod(), paramTypes);
    }
}
