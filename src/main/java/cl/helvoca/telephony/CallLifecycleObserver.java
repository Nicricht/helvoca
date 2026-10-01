package cl.helvoca.telephony;

import cl.helvoca.call.CallSession;

/**
 * In-process lifecycle hooks that run inside the same call transaction.
 * Implementations must be idempotent because carrier/provider callbacks may replay.
 */
public interface CallLifecycleObserver {
    default void onInboundCallStarted(CallSession call) {}
    default void onCallUpdated(CallSession call) {}
}
