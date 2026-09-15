package cl.helvoca.jobs;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PersistentJobHandlerRegistry {
    private final Map<PersistentJob.Type, PersistentJobHandler> handlers;

    public PersistentJobHandlerRegistry(List<PersistentJobHandler> handlers) {
        EnumMap<PersistentJob.Type, PersistentJobHandler> byType = new EnumMap<>(PersistentJob.Type.class);
        for (PersistentJobHandler handler : handlers == null ? List.<PersistentJobHandler>of() : handlers) {
            PersistentJobHandler previous = byType.put(handler.type(), handler);
            if (previous != null) {
                throw new IllegalStateException("Multiple persistent job handlers registered for " + handler.type());
            }
        }
        this.handlers = Map.copyOf(byType);
    }

    public PersistentJobHandler require(PersistentJob.Type type) {
        PersistentJobHandler handler = handlers.get(type);
        if (handler == null) throw new PersistentJobHandler.PermanentJobException("No handler registered for durable job type " + type);
        return handler;
    }
}
