package pub.module.mqrpc.tx;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 当前线程 RPC 全局事务上下文。
 */
public final class RpcTxContext {

    private static final ThreadLocal<State> HOLDER = new ThreadLocal<>();

    private RpcTxContext() {
    }

    public static String globalXid() {
        State state = HOLDER.get();
        return state != null ? state.globalXid : null;
    }

    public static boolean isCompensating() {
        State state = HOLDER.get();
        return state != null && state.compensating;
    }

    public static void begin(String globalXid) {
        State state = new State();
        state.globalXid = globalXid;
        HOLDER.set(state);
    }

    public static void pushBranch(String branchId) {
        State state = HOLDER.get();
        if (state != null) {
            state.branches.push(branchId);
        }
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static void runAsCompensating(Runnable action) {
        State state = HOLDER.get();
        boolean previous = state != null && state.compensating;
        if (state != null) {
            state.compensating = true;
        }
        try {
            action.run();
        } finally {
            if (state != null) {
                state.compensating = previous;
            }
        }
    }

    private static final class State {
        private String globalXid;
        private boolean compensating;
        private final Deque<String> branches = new ArrayDeque<>();
    }
}
