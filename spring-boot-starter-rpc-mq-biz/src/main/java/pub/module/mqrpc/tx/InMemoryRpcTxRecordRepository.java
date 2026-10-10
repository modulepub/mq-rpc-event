package pub.module.mqrpc.tx;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内内存实现，事务元数据通过 MQ {@link pub.module.mqrpc.RpcEnvelope} 传递。
 */
public class InMemoryRpcTxRecordRepository implements RpcTxRecordRepository {

    private final ConcurrentHashMap<String, RpcTxRecord> store = new ConcurrentHashMap<>();

    @Override
    public void save(RpcTxRecord record) {
        store.put(record.getBranchId(), record);
    }

    @Override
    public void updateStatus(String branchId, RpcTxStatus status, String errorMessage) {
        store.computeIfPresent(branchId, (id, record) -> {
            record.setStatus(status);
            record.setErrorMessage(errorMessage);
            record.setUpdatedAt(java.time.Instant.now());
            return record;
        });
    }

    @Override
    public void incrementCompensateAttempts(String branchId) {
        store.computeIfPresent(branchId, (id, record) -> {
            record.setCompensateAttempts(record.getCompensateAttempts() + 1);
            record.setUpdatedAt(java.time.Instant.now());
            return record;
        });
    }

    @Override
    public Optional<RpcTxRecord> findByBranchId(String branchId) {
        return Optional.ofNullable(store.get(branchId));
    }

    @Override
    public List<RpcTxRecord> findByGlobalXid(String globalXid) {
        return store.values().stream()
                .filter(r -> globalXid.equals(r.getGlobalXid()))
                .sorted(java.util.Comparator.comparing(RpcTxRecord::getCreatedAt))
                .toList();
    }

    @Override
    public List<RpcTxRecord> findByStatus(RpcTxStatus status, int limit) {
        return store.values().stream()
                .filter(r -> r.getStatus() == status)
                .sorted(java.util.Comparator.comparing(RpcTxRecord::getUpdatedAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<RpcTxRecord> findRecent(int limit) {
        return store.values().stream()
                .sorted(java.util.Comparator.comparing(RpcTxRecord::getUpdatedAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<RpcTxRecord> findNeedsAttention(int limit) {
        return store.values().stream()
                .filter(r -> r.getStatus() == RpcTxStatus.COMPENSATE_PENDING
                        || r.getStatus() == RpcTxStatus.COMPENSATE_FAILED
                        || r.getStatus() == RpcTxStatus.NEEDS_MANUAL)
                .sorted(java.util.Comparator.comparing(RpcTxRecord::getUpdatedAt).reversed())
                .limit(limit)
                .toList();
    }
}
