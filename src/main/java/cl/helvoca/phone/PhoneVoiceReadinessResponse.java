package cl.helvoca.phone;

import java.time.Instant;
import java.util.UUID;

/**
 * Tenant-scoped, database-only voice setup diagnostic.
 *
 * A historical attestation is NOT proof that Twilio is configured correctly
 * right now, and is never presented as end-to-end call readiness.
 * Carrier reconciliation remains part of an explicitly authorized activation.
 */
public record PhoneVoiceReadinessResponse(
        UUID phoneId,
        String phoneNumber,
        String state,
        boolean canAttemptActivation,
        Instant lastVerifiedAt,
        String message
) {
    public static PhoneVoiceReadinessResponse from(PhoneNumber phone, String managedAccountSid) {
        String state;
        boolean canAttemptActivation = false;
        String message;
        if (!"TWILIO".equalsIgnoreCase(phone.getProvider())) {
            state = "UNSUPPORTED_PROVIDER";
            message = "Este número no utiliza el proveedor de voz Twilio habilitado.";
        } else if (!"MANAGED_PURCHASE".equals(phone.getTwilioBindingSource())
                || phone.getTwilioAccountSid() == null
                || phone.getTwilioAccountSid().isBlank()
                || phone.getExternalId() == null
                || !phone.getExternalId().matches("PN[0-9a-fA-F]{32}")) {
            state = "UNVERIFIED_BINDING";
            message = "Falta evidencia de pertenencia o SID; requiere conciliación segura.";
        } else if (managedAccountSid == null
                || !phone.getTwilioAccountSid().equals(managedAccountSid)) {
            state = "ACCOUNT_MISMATCH";
            message = "La cuenta Twilio administrada no coincide con la asignación registrada.";
        } else if (phone.getVoiceVerifiedAt() == null) {
            state = "NEEDS_VERIFICATION";
            canAttemptActivation = true;
            message = "El número está asignado, pero aún no registra verificación de voz.";
        } else if (!phone.isActive()) {
            state = "ACTIVATION_REQUIRED";
            canAttemptActivation = true;
            message = "Hay una verificación anterior. Activar requiere volver a validar Twilio.";
        } else {
            state = "PREVIOUSLY_VERIFIED";
            message = "Activo con verificación anterior. No confirma por sí solo llamadas reales ni webhooks actuales.";
        }
        return new PhoneVoiceReadinessResponse(phone.getId(), phone.getPhoneNumber(), state,
                canAttemptActivation, phone.getVoiceVerifiedAt(), message);
    }
}
