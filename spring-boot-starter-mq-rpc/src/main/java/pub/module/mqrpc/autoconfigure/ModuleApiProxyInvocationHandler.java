package pub.module.mqrpc.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import pub.module.mqrpc.RpcEnvelope;
import pub.module.mqrpc.RpcResult;
import pub.module.mqrpc.tx.RpcTxCoordinator;
import pub.module.mqrpc.tx.RpcTxSupport;
import pub.module.mqrpc.tx.RpcTxContext;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * ModuleApi 动态代理：AUTO 路由（本 JVM 有 Provider → 本地直调，否则 → MQ）。
 */
@Slf4j
public class ModuleApiProxyInvocationHandler implements InvocationHandler {

    private final Class<?> apiInterface;
    private final String module;
    private final ModuleApiProviderRegistry providerRegistry;
    private final MqRpcGateway mqRpcGateway;
    private final RpcTxCoordinator txCoordinator;

    public ModuleApiProxyInvocationHandler(
            Class<?> apiInterface,
            ModuleApiProviderRegistry providerRegistry,
            MqRpcGateway mqRpcGateway,
            RpcTxCoordinator txCoordinator) {
        this.apiInterface = apiInterface;
        this.module = ModuleApiSupport.resolveModule(apiInterface);
        this.providerRegistry = providerRegistry;
        this.mqRpcGateway = mqRpcGateway;
        this.txCoordinator = txCoordinator;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }
        if (providerRegistry.hasProvider(apiInterface)) {
            log.info("ModuleRpc [LOCAL] module={} service={} method={}",
                    module, apiInterface.getSimpleName(), method.getName());
            return providerRegistry.invoke(apiInterface, method, args);
        }

        RpcEnvelope envelope = buildEnvelope(method, args);
        String branchId = prepareTransactionalBranch(envelope, method.getName());
        try {
            RpcResult result = mqRpcGateway.request(envelope);
            mqRpcGateway.throwIfFailed(result);
            txCoordinator.confirm(branchId);
            return result.getData();
        } catch (Throwable ex) {
            txCoordinator.fail(branchId, ex.getMessage());
            if (branchId != null && txCoordinator.isEnabled()) {
                txCoordinator.markNeedsManual(branchId,
                        "正向 RPC 失败，请检查对端状态后手动补偿: " + ex.getMessage());
            }
            throw ex;
        }
    }

    private String prepareTransactionalBranch(RpcEnvelope envelope, String methodName) {
        if (!txCoordinator.isEnabled() || RpcTxContext.isCompensating()) {
            return null;
        }
        if (!RpcTxSupport.isTransactional(apiInterface)) {
            return null;
        }
        if (RpcTxSupport.isCompensateMethod(apiInterface, methodName)) {
            return null;
        }
        String compensateMethod = RpcTxSupport.resolveCompensateMethod(apiInterface, methodName);
        String globalXid = txCoordinator.ensureGlobalXid();
        txCoordinator.registerRollbackHook(globalXid);
        return txCoordinator.beginBranch(globalXid, envelope, compensateMethod);
    }

    private RpcEnvelope buildEnvelope(Method method, Object[] args) {
        RpcEnvelope envelope = new RpcEnvelope();
        envelope.setModule(module);
        envelope.setService(apiInterface.getName());
        envelope.setMethod(method.getName());
        envelope.setParameterTypes(ModuleApiSupport.parameterTypeNames(method.getParameterTypes()));
        envelope.setArgs(args);
        envelope.setTraceId(UUID.randomUUID().toString());
        if (RpcTxContext.globalXid() != null) {
            envelope.setTxXid(RpcTxContext.globalXid());
        }
        return envelope;
    }
}
