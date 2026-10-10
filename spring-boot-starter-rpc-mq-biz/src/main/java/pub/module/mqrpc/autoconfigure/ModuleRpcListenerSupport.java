package pub.module.mqrpc.autoconfigure;

import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.messaging.handler.annotation.support.MessageHandlerMethodFactory;

final class ModuleRpcListenerSupport {

    private static final MessageHandlerMethodFactory MESSAGE_HANDLER_METHOD_FACTORY = createMessageHandlerMethodFactory();

    private ModuleRpcListenerSupport() {
    }

    static MessageHandlerMethodFactory messageHandlerMethodFactory() {
        return MESSAGE_HANDLER_METHOD_FACTORY;
    }

    private static MessageHandlerMethodFactory createMessageHandlerMethodFactory() {
        DefaultMessageHandlerMethodFactory factory = new DefaultMessageHandlerMethodFactory();
        try {
            factory.afterPropertiesSet();
        } catch (Exception ex) {
            throw new IllegalStateException("初始化 ModuleRpc MessageHandlerMethodFactory 失败", ex);
        }
        return factory;
    }
}
