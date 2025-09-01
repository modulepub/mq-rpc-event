package pub.module.mqrpc.tx;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pub.module.mqrpc.RpcEnvelope;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * RPC Saga 协调器：记录分支、确认/失败、触发补偿。
 */
@Slf4j
public class RpcTxCoordinator {

    private final RpcTxRecordRepository repository;
    private final RpcTxCompensator compensator;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public RpcTxCoordinator(RpcTxRecordRepository repository,
                            RpcTxCompensator compensator,
                            ObjectMapper objectMapper,
                            boolean enabled) {
        this.repository = repository;
        this.compensator = compensator;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String newGlobalXid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public String ensureGlobalXid() {
        String existing = RpcTxContext.globalXid();
        if (existing != null) {
            return existing;
        }
        String xid = newGlobalXid();
        RpcTxContext.begin(xid);
        return xid;
    }

    public void registerRollbackHook(String globalXid) {
        if (!enabled || globalXid == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    log.warn("ModuleRpc 检测到本地事务回滚，触发全局补偿 globalXid={}", globalXid);
                    compensateGlobal(globalXid);
                }
                RpcTxContext.clear();
            }
        });
    }

    public String beginBranch(String globalXid, RpcEnvelope envelope, String compensateMethod) {
        if (!enabled || globalXid == null) {
            return null;
        }
        String branchId = UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        RpcTxRecord record = new RpcTxRecord();
        record.setBranchId(branchId);
        record.setGlobalXid(globalXid);
        record.setModule(envelope.getModule());
        record.setService(envelope.getService());
        record.setMethod(envelope.getMethod());
        record.setCompensateMethod(compensateMethod);
        record.setArgsJson(toArgsJson(envelope.getArgs()));
        record.setStatus(RpcTxStatus.PENDING);
        record.setCompensateAttempts(0);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        repository.save(record);
        RpcTxContext.pushBranch(branchId);
        envelope.setBranchId(branchId);
        envelope.setTxXid(globalXid);
        log.info("ModuleRpc [TX] 分支已登记 branchId={} globalXid={} {}.{}",
                branchId, globalXid, envelope.getService(), envelope.getMethod());
        return branchId;
    }

    public void confirm(String branchId) {
        if (!enabled || branchId == null) {
            return;
        }
        repository.updateStatus(branchId, RpcTxStatus.CONFIRMED, null);
        log.info("ModuleRpc [TX] 分支已确认 branchId={}", branchId);
    }

    public void fail(String branchId, String errorMessage) {
        if (!enabled || branchId == null) {
            return;
        }
        repository.updateStatus(branchId, RpcTxStatus.FAILED, errorMessage);
        log.warn("ModuleRpc [TX] 分支失败 branchId={} error={}", branchId, errorMessage);
    }

    public void markNeedsManual(String branchId, String errorMessage) {
        if (!enabled || branchId == null) {
            return;
        }
        repository.updateStatus(branchId, RpcTxStatus.NEEDS_MANUAL, errorMessage);
    }

    public void compensateGlobal(String globalXid) {
        if (!enabled || globalXid == null) {
            return;
        }
        List<RpcTxRecord> branches = repository.findByGlobalXid(globalXid);
        for (int i = branches.size() - 1; i >= 0; i--) {
            RpcTxRecord branch = branches.get(i);
            if (branch.getStatus() == RpcTxStatus.CONFIRMED
                    || branch.getStatus() == RpcTxStatus.COMPENSATE_PENDING
                    || branch.getStatus() == RpcTxStatus.COMPENSATE_FAILED) {
                compensateBranch(branch.getBranchId());
            }
        }
    }

    public void compensateBranch(String branchId) {
        if (!enabled || branchId == null) {
            return;
        }
        repository.findByBranchId(branchId).ifPresent(branch -> {
            if (branch.getStatus() == RpcTxStatus.COMPENSATED) {
                return;
            }
            repository.updateStatus(branchId, RpcTxStatus.COMPENSATE_PENDING, branch.getErrorMessage());
            repository.incrementCompensateAttempts(branchId);
            try {
                compensator.compensate(branch);
                repository.updateStatus(branchId, RpcTxStatus.COMPENSATED, null);
                log.info("ModuleRpc [TX] 补偿成功 branchId={} method={}", branchId, branch.getCompensateMethod());
            } catch (Exception ex) {
                repository.updateStatus(branchId, RpcTxStatus.COMPENSATE_FAILED, ex.getMessage());
                log.error("ModuleRpc [TX] 补偿失败 branchId={} error={}", branchId, ex.getMessage(), ex);
            }
        });
    }

    public RpcTxRecordRepository getRepository() {
        return repository;
    }

    private String toArgsJson(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(args);
        } catch (Exception ex) {
            log.warn("RPC 事务参数序列化失败: {}", ex.getMessage());
            return "[]";
        }
    }
}
