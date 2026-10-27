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
                "", "http://recepvoz.example.test", "https://user@recepvoz.example.test",
                "https://recepvoz.example.test/path", "https://recepvoz.example.test?q=1",
                "https://recepvoz.example.test#fragment" }) {
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
        assertThrows(IllegalArgumentException.class,
                () -> mail.sendPasswordReset("owner@example.test\nBcc:evil@example.test", TOKEN));
        verifyNoMoreInteractions(sender);
    }
}
