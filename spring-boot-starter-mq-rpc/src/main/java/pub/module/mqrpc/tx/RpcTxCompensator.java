package pub.module.mqrpc.tx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import pub.module.mqrpc.RpcEnvelope;
import pub.module.mqrpc.RpcResult;
import pub.module.mqrpc.autoconfigure.ModuleApiProviderRegistry;
import pub.module.mqrpc.autoconfigure.ModuleApiSupport;
import pub.module.mqrpc.autoconfigure.MqRpcGateway;

import java.lang.reflect.Method;

/**
 * 执行补偿 RPC（本地或 MQ）。
 */
@Slf4j
public class RpcTxCompensator {

    private final ModuleApiProviderRegistry providerRegistry;
    private final MqRpcGateway mqRpcGateway;
    private final ObjectMapper objectMapper;

    public RpcTxCompensator(ModuleApiProviderRegistry providerRegistry,
                            MqRpcGateway mqRpcGateway,
                            ObjectMapper objectMapper) {
        this.providerRegistry = providerRegistry;
        this.mqRpcGateway = mqRpcGateway;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public void compensate(RpcTxRecord branch) throws Exception {
        Class<?> apiInterface = Class.forName(branch.getService());
        if (!ModuleApiSupport.isModuleApiInterface(apiInterface)) {
            throw new IllegalStateException("非 RPC 契约: " + branch.getService());
        }
        String compensateMethod = branch.getCompensateMethod();
        Method method = resolveMethod(apiInterface, compensateMethod);
        Object[] args = deserializeArgs(branch.getArgsJson(), method.getParameterTypes());

        RpcTxContext.runAsCompensating(() -> {
            try {
                if (providerRegistry.hasProvider(apiInterface)) {
                    log.info("ModuleRpc [TX-COMPENSATE] LOCAL {}.{}", apiInterface.getSimpleName(), compensateMethod);
                    method.invoke(providerRegistry.getProvider(apiInterface), args);
                } else {
                    RpcEnvelope envelope = new RpcEnvelope();
                    envelope.setModule(branch.getModule());
                    envelope.setService(branch.getService());
                    envelope.setMethod(compensateMethod);
                    envelope.setParameterTypes(ModuleApiSupport.parameterTypeNames(method.getParameterTypes()));
                    envelope.setArgs(args);
                    envelope.setTxXid(branch.getGlobalXid());
                    envelope.setBranchId(branch.getBranchId());
                    envelope.setCompensateInvocation(true);
                    log.info("ModuleRpc [TX-COMPENSATE] MQ {}.{}", apiInterface.getSimpleName(), compensateMethod);
                    RpcResult result = mqRpcGateway.request(envelope);
                    mqRpcGateway.throwIfFailed(result);
                }
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException("补偿调用失败: " + compensateMethod, ex);
            }
        });
    }

    private static Method resolveMethod(Class<?> apiInterface, String methodName) throws NoSuchMethodException {
        for (Method method : apiInterface.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        throw new NoSuchMethodException(apiInterface.getName() + "." + methodName);
    }

    private Object[] deserializeArgs(String argsJson, Class<?>[] paramTypes) {
        if (paramTypes.length == 0) {
            return new Object[0];
        }
        try {
            JsonNode node = objectMapper.readTree(argsJson != null ? argsJson : "[]");
            if (!node.isArray()) {
                return new Object[paramTypes.length];
            }
            Object[] args = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                if (i < node.size() && !node.get(i).isNull()) {
                    args[i] = objectMapper.treeToValue(node.get(i), paramTypes[i]);
                }
            }
            return args;
        } catch (Exception ex) {
            throw new IllegalStateException("补偿参数反序列化失败", ex);
        }
    }
}
