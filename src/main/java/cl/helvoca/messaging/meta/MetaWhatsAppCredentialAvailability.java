package cl.helvoca.messaging.meta;

@FunctionalInterface
public interface MetaWhatsAppCredentialAvailability {
    boolean isAvailable(String credentialRef);
}
