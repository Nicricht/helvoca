package cl.helvoca.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessAccountMailServiceTest {
    private static final String TOKEN = "a".repeat(43);

    @SuppressWarnings("unchecked")
    private ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    @Test
    void disabledMailNeverLooksUpOrCallsASenderAndCannotEnableVerificationAlone() {
        ObjectProvider<JavaMailSender> provider = provider(mock(JavaMailSender.class));
        clearInvocations(provider);
        var mail = new BusinessAccountMailService(provider, false, false, false, "", "");
        assertFalse(mail.isEnabled());
        assertFalse(mail.isVerificationRequired());
        assertFalse(mail.isInvitationDeliveryEnabled());
        verifyNoInteractions(provider);
        assertThrows(IllegalStateException.class, () -> mail.sendPasswordReset("owner@example.test", TOKEN));
        assertThrows(IllegalStateException.class, () -> mail.sendInvitation("owner@example.test", null));
        assertThrows(IllegalStateException.class,
                () -> new BusinessAccountMailService(provider, false, true, false, "", ""));
        assertThrows(IllegalStateException.class,
                () -> new BusinessAccountMailService(provider, false, false, true, "", ""));
    }

    @Test
    void enabledEmailRequiresRealSmtpSenderValidFromAndStrictHttpsOrigin() {
        ObjectProvider<JavaMailSender> missing = provider(null);
        assertThrows(IllegalStateException.class,
                () -> new BusinessAccountMailService(missing, true, false, false,
                        "identity@example.test", "https://recepvoz.example.test"));
        ObjectProvider<JavaMailSender> available = provider(mock(JavaMailSender.class));
        assertThrows(IllegalArgumentException.class,
                () -> new BusinessAccountMailService(available, true, false, false,
                        "invalid", "https://recepvoz.example.test"));
        for (String url : new String[] {
                null, "", "http://recepvoz.example.test", "https://user@recepvoz.example.test",
                "https://recepvoz.example.test/path", "https://recepvoz.example.test?q=1",
                "https://recepvoz.example.test#fragment", "https:/relative-path", "https://%bad",
                "https://recepvoz.example.test/%zz" }) {
            assertThrows(IllegalStateException.class,
                    () -> new BusinessAccountMailService(available, true, false, false,
                            "identity@example.test", url));
        }
    }

    @Test
    void enabledResetAndVerificationSendFragmentLinksWithoutLoggingOrReturningSecrets() {
        JavaMailSender sender = mock(JavaMailSender.class);
        var mail = new BusinessAccountMailService(provider(sender), true, true, true,
                "identity@example.test", "https://RecepVoz.Example.Test/");
        assertTrue(mail.isEnabled());
        assertTrue(mail.isVerificationRequired());
        assertTrue(mail.isInvitationDeliveryEnabled());

        mail.sendPasswordReset("owner@example.test", TOKEN);
        mail.sendVerification("owner@example.test", TOKEN);
        var captured = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, times(2)).send(captured.capture());
        assertArrayEquals(new String[] {"owner@example.test"}, captured.getAllValues().get(0).getTo());
        assertEquals("identity@example.test", captured.getAllValues().get(0).getFrom());
        assertTrue(captured.getAllValues().get(0).getText()
                .contains("https://recepvoz.example.test/app/password-reset#token=" + TOKEN));
        assertTrue(captured.getAllValues().get(1).getText()
                .contains("https://recepvoz.example.test/app/verify-email#token=" + TOKEN));
        assertFalse(captured.getAllValues().get(0).getText().contains("?token="));
        assertThrows(IllegalArgumentException.class, () -> mail.sendPasswordReset("owner@example.test", "bad"));
        assertThrows(IllegalArgumentException.class, () -> mail.sendPasswordReset("owner@example.test", null));
        for (String recipient : new String[] { "", "  ", "owner@example.test\rBcc:evil@example.test",
                "Alice <owner@example.test>", "owner.example.test",
                "owner@@example.test", "owner@ex ample.test" }) {
            assertThrows(IllegalArgumentException.class,
                    () -> mail.sendPasswordReset(recipient, TOKEN));
        }
        assertThrows(IllegalArgumentException.class, () -> mail.sendPasswordReset(null, TOKEN));
        assertThrows(IllegalArgumentException.class,
                () -> mail.sendPasswordReset("owner@example.test\nBcc:evil@example.test", TOKEN));
        verifyNoMoreInteractions(sender);
    }


    @SuppressWarnings("unchecked")
    private ObjectProvider<ResendHttpsSender> httpsProvider(ResendHttpsSender sender) {
        ObjectProvider<ResendHttpsSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    @Test
    void resendHttpsTransportDoesNotRequireSmtpAndUsesExistingValidation() {
        ResendHttpsSender https = mock(ResendHttpsSender.class);
        when(https.isConfigured()).thenReturn(true);
        var mail = new BusinessAccountMailService(provider(null), true, false, false,
                "identity@example.test", "https://recepvoz.example.test",
                "RESEND_HTTPS", httpsProvider(https));
        mail.sendPasswordReset("owner@example.test", TOKEN);
        verify(https).send(any(SimpleMailMessage.class));
        assertThrows(IllegalArgumentException.class,
                () -> mail.sendPasswordReset("not-an-email", TOKEN));
        var missing = httpsProvider(null);
        assertThrows(IllegalStateException.class,
                () -> new BusinessAccountMailService(provider(null), true, false, false,
                        "identity@example.test", "https://recepvoz.example.test",
                        "RESEND_HTTPS", missing));
        assertThrows(IllegalStateException.class,
                () -> new BusinessAccountMailService(provider(null), true, false, false,
                        "identity@example.test", "https://recepvoz.example.test",
                        "UNKNOWN", httpsProvider(https)));
    }


    @Test
    void httpsTransportFailsClosedWithMissingKeyOrAbsentProvider() {
        ResendHttpsSender missingKey = mock(ResendHttpsSender.class);
        when(missingKey.isConfigured()).thenReturn(false);
        assertThrows(IllegalStateException.class,
                () -> new BusinessAccountMailService(provider(null), true, false, false,
                        "identity@example.test", "https://recepvoz.example.test",
                        "RESEND_HTTPS", httpsProvider(missingKey)));
        assertThrows(IllegalStateException.class,
                () -> new BusinessAccountMailService(provider(null), true, false, false,
                        "identity@example.test", "https://recepvoz.example.test",
                        "RESEND_HTTPS", null));

        var disabled = new BusinessAccountMailService(provider(null), false, false, false,
                "", "", "RESEND_HTTPS", null);
        assertFalse(disabled.isEnabled());
        assertThrows(IllegalStateException.class,
                () -> disabled.sendPasswordReset("owner@example.test", TOKEN));
    }

    @Test
    void invitationDeliveryReusesOnlyExistingCanonicalOneUseInvitePath() {
        JavaMailSender sender = mock(JavaMailSender.class);
        var mail = new BusinessAccountMailService(provider(sender), true, false, true,
                "identity@example.test", "https://recepvoz.example.test");
        String path = "/app/invite?businessId=11111111-1111-1111-1111-111111111111&token=" + TOKEN;
        mail.sendInvitation("operator@example.test", path);
        var captured = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captured.capture());
        assertTrue(captured.getValue().getText().contains("https://recepvoz.example.test" + path));
        for (String pathValue : new String[] {
                "https://evil.example/invite?token=" + TOKEN,
                "/app/invite?businessId=wrong&token=" + TOKEN,
                path + "&other=abc",
                path + "\nBcc: attacker@example.test" }) {
            assertThrows(IllegalArgumentException.class,
                    () -> mail.sendInvitation("operator@example.test", pathValue));
        }
        assertThrows(IllegalArgumentException.class,
                () -> mail.sendInvitation("operator@example.test", null));
        assertThrows(IllegalArgumentException.class,
                () -> mail.sendInvitation("operator@example.test",
                        "/app/invite?businessId=" + "0".repeat(36) + "&token=" + TOKEN));
        verifyNoMoreInteractions(sender);

        var notEnabled = new BusinessAccountMailService(provider(mock(JavaMailSender.class)),
                true, false, false, "identity@example.test", "https://recepvoz.example.test");
        assertThrows(IllegalStateException.class,
                () -> notEnabled.sendInvitation("operator@example.test", path));
    }
}
