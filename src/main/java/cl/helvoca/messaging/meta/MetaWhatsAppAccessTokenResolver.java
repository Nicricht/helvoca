package cl.helvoca.messaging.meta;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves an outbound Meta access token for one tenant without exposing
 * credential storage details to the messaging provider.
 */
@FunctionalInterface
public interface MetaWhatsAppAccessTokenResolver {
    Optional<String> resolve(UUID businessId);
}
