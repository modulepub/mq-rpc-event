package tg.rpc.mqrpc.admin;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pub.module.mqrpc.autoconfigure.RpcTxAdminPaths;

/**
 * RPC 分布式事务管理页（由 {@link pub.module.mqrpc.autoconfigure.ModuleRpcAutoConfiguration} 注册）。
 */
@Controller
@RequestMapping(RpcTxAdminPaths.BASE)
public class RpcTxAdminPageController {

    private static final String INDEX_HTML = "pub-tg-rpc-tx-admin/index.html";

    @GetMapping(value = {"", "/", "/index.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> index() {
        ClassPathResource resource = new ClassPathResource(INDEX_HTML);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(resource);
    }
}
