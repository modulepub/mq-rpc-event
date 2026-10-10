package pub.module.mqrpc.tx;

import java.util.List;
import java.util.Optional;

public interface RpcTxRecordRepository {

    void save(RpcTxRecord record);

    void updateStatus(String branchId, RpcTxStatus status, String errorMessage);

    void incrementCompensateAttempts(String branchId);

    Optional<RpcTxRecord> findByBranchId(String branchId);

    List<RpcTxRecord> findByGlobalXid(String globalXid);

    List<RpcTxRecord> findByStatus(RpcTxStatus status, int limit);

    List<RpcTxRecord> findRecent(int limit);

    List<RpcTxRecord> findNeedsAttention(int limit);
}
