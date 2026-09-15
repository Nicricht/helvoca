package cl.helvoca.operations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Transaction-scoped execution fence for a tenant operation.
 *
 * PostgreSQL advisory transaction locks are released automatically on commit or
 * rollback. Callers must invoke this service from an active transaction before
 * any materialization or external side effect for the operation.
 */
@Service
public class OperationExecutionLockService {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final JdbcTemplate jdbc;

    public OperationExecutionLockService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lock(UUID businessId, UUID operationId) {
        if (businessId == null || operationId == null) {
            throw new IllegalArgumentException("businessId and operationId are required for an execution lock.");
        }
        long key = advisoryKey(businessId, operationId);
        jdbc.execute("SELECT pg_advisory_xact_lock(" + key + ")");
    }

    static long advisoryKey(UUID businessId, UUID operationId) {
        long hash = FNV_OFFSET_BASIS;
        hash = mixLong(hash, businessId.getMostSignificantBits());
        hash = mixLong(hash, businessId.getLeastSignificantBits());
        hash = mixLong(hash, operationId.getMostSignificantBits());
        return mixLong(hash, operationId.getLeastSignificantBits());
    }

    private static long mixLong(long hash, long value) {
        long current = hash;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            current ^= (value >>> shift) & 0xffL;
            current *= FNV_PRIME;
        }
        return current;
    }
}
