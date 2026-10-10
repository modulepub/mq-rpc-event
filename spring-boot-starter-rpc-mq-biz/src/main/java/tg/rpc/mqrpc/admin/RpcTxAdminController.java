package tg.rpc.mqrpc.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pub.module.mqrpc.autoconfigure.RpcTxAdminPaths;
import pub.module.mqrpc.tx.RpcTxCoordinator;
import pub.module.mqrpc.tx.RpcTxRecord;
import pub.module.mqrpc.tx.RpcTxStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RPC 分布式事务异常记录与手动补偿 API（由 {@link pub.module.mqrpc.autoconfigure.ModuleRpcAutoConfiguration} 注册）。
 */
@RestController
@RequestMapping(RpcTxAdminPaths.API)
public class RpcTxAdminController {

    private final RpcTxCoordinator coordinator;

    public RpcTxAdminController(RpcTxCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @GetMapping("/records")
    public List<RpcTxRecord> list(
            @RequestParam(defaultValue = "attention") String filter,
            @RequestParam(defaultValue = "100") int limit) {
        return switch (filter) {
            case "all" -> coordinator.getRepository().findRecent(limit);
            case "status" -> coordinator.getRepository().findByStatus(RpcTxStatus.COMPENSATE_FAILED, limit);
            default -> coordinator.getRepository().findNeedsAttention(limit);
        };
    }

    @GetMapping("/records/{branchId}")
    public ResponseEntity<RpcTxRecord> detail(@PathVariable String branchId) {
        return coordinator.getRepository().findByBranchId(branchId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/records/global/{globalXid}")
    public List<RpcTxRecord> byGlobal(@PathVariable String globalXid) {
        return coordinator.getRepository().findByGlobalXid(globalXid);
    }

    @PostMapping("/records/{branchId}/compensate")
    public Map<String, Object> compensateBranch(@PathVariable String branchId) {
        coordinator.compensateBranch(branchId);
        Map<String, Object> body = new HashMap<>();
        body.put("branchId", branchId);
        body.put("message", "补偿已触发");
        coordinator.getRepository().findByBranchId(branchId)
                .ifPresent(r -> body.put("status", r.getStatus().name()));
        return body;
    }

    @PostMapping("/global/{globalXid}/compensate")
    public Map<String, Object> compensateGlobal(@PathVariable String globalXid) {
        coordinator.compensateGlobal(globalXid);
        Map<String, Object> body = new HashMap<>();
        body.put("globalXid", globalXid);
        body.put("message", "全局补偿已触发");
        return body;
    }
}
