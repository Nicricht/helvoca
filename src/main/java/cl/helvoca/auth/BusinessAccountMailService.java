package cl.helvoca.auth;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The sole outbound boundary for business identity email. OFF without an
 * explicit operator opt-in and a configured HTTPS origin + SMTP sender.
 */
@Service
public final class BusinessAccountMailService {
    private static final Pattern INVITE_PATH = Pattern.compile(
            "^/app/invite\\?businessId=([0-9a-fA-F-]{36})&token=([A-Za-z0-9_-]{43})$");
    private final JavaMailSender sender;
    private final boolean enabled;
    private final boolean verificationRequired;
    private final boolean invitationDeliveryEnabled;
    private final String from;
    private final String publicOrigin;

    public BusinessAccountMailService(
            ObjectProvider<JavaMailSender> providers,
            @Value("${app.account-email.enabled:false}") boolean enabled,
            @Value("${app.account-email.verification-required:false}") boolean verificationRequired,
            @Value("${app.account-email.invitation-delivery-enabled:false}") boolean invitationDeliveryEnabled,
            @Value("${app.account-email.from:}") String from,
            @Value("${app.account-email.public-base-url:}") String baseUrl) {
        if (!enabled && (verificationRequired || invitationDeliveryEnabled)) {
            throw new IllegalStateException("Account email enrollment requires the outbound email opt-in");
        }
        this.sender = enabled ? providers.getIfAvailable() : null;
        this.enabled = enabled;
        this.verificationRequired = verificationRequired;
        this.invitationDeliveryEnabled = invitationDeliveryEnabled;
        if (enabled && sender == null) {
            throw new IllegalStateException("Account email requires an explicitly configured SMTP sender");
        }
        this.from = enabled ? validAddress(from) : null;
        this.publicOrigin = enabled ? httpsOrigin(baseUrl) : null;
    }

    public boolean isEnabled() { return enabled; }
    public boolean isVerificationRequired() { return verificationRequired; }
    public boolean isInvitationDeliveryEnabled() { return invitationDeliveryEnabled; }

    public void sendPasswordReset(String recipient, String token) {
        send(recipient, token, "Recupera tu contraseña de RecepVoz",
                "Solicitaste recuperar tu contraseña. Abre este enlace dentro de 30 minutos:\n\n"
                        + publicOrigin + "/app/password-reset#token=" + token
                        + "\n\nSi no hiciste la solicitud, ignora este mensaje.");
    }

    public void sendVerification(String recipient, String token) {
        send(recipient, token, "Confirma tu correo de RecepVoz",
                "Para confirmar tu correo, abre este enlace dentro de 24 horas:\n\n"
                        + publicOrigin + "/app/verify-email#token=" + token
                        + "\n\nSi no creaste esta cuenta, ignora este mensaje.");
    }

    /** Send the already-created manual invitation; never mint or rotate one. */
    public void sendInvitation(String recipient, String invitePath) {
        if (!enabled || !invitationDeliveryEnabled) {
            throw new IllegalStateException("Automatic invitation delivery is disabled");
        }
        Matcher match = INVITE_PATH.matcher(invitePath == null ? "" : invitePath);
        if (!match.matches()) throw new IllegalArgumentException("Invalid invitation path");
        try {
            UUID.fromString(match.group(1));
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("Invalid invitation business", malformed);
        }
        send(recipient, match.group(2), "Invitación personal a RecepVoz",
                "Te invitaron a colaborar en un negocio de RecepVoz. "
                        + "Abre este enlace privado dentro de 72 horas:\n\n"
                        + publicOrigin + invitePath
                        + "\n\nNo compartas este enlace con otras personas.");
    }

    private void send(String recipient, String token, String subject, String body) {
        if (!enabled) throw new IllegalStateException("Account email is disabled");
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("Invalid outbound account token");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(validAddress(recipient));
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }

    private static String validAddress(String raw) {
        try {
            if (raw == null || raw.isBlank() || raw.contains("\r") || raw.contains("\n")) {
                throw new IllegalArgumentException("Account email address is required");
            }
            InternetAddress address = new InternetAddress(raw.trim(), true);
            address.validate();
            if (!address.getAddress().equals(raw.trim()) || !address.getAddress().contains("@")) {
                throw new IllegalArgumentException("Invalid account email address");
            }
            return address.getAddress();
        } catch (AddressException invalid) {
            throw new IllegalArgumentException("Invalid account email address", invalid);
        }
    }

    private static String httpsOrigin(String raw) {
        try {
            URI parsed = URI.create(raw == null ? "" : raw.trim());
            if (!"https".equals(parsed.getScheme()) || parsed.getHost() == null
                    || parsed.getUserInfo() != null || parsed.getQuery() != null
                    || parsed.getFragment() != null || (parsed.getPath() != null
                    && !parsed.getPath().isEmpty() && !"/".equals(parsed.getPath()))) {
                throw new IllegalArgumentException("Account email public origin must be HTTPS");
            }
            return parsed.getScheme() + "://" + parsed.getRawAuthority().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("Invalid account email public HTTPS origin", invalid);
        }
    }
}
