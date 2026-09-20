package cl.helvoca.messaging.meta;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the outbound Meta access token for one tenant.
 *
 * No implementation is registered yet. Production therefore remains fail-closed
 * until secure tenant credentials are added in the next block.
 */
@FunctionalInterface
public interface MetaWhatsAppAccessTokenResolver {
    Optional<String> resolve(UUID businessId);
}
