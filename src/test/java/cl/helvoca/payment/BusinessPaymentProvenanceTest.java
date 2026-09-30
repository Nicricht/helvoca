package cl.helvoca.payment;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class BusinessPaymentProvenanceTest {

    @Test
    void successfulPaymentGetsProviderDefaultsAndVerifiedTimestampOnPersist() {
        BusinessPayment payment = new BusinessPayment();
        payment.setStatus(BusinessPayment.Status.SUCCEEDED);
        payment.setVerificationMethod(null);
        payment.setPaymentMethod(null);

        payment.prePersist();

        assertEquals(BusinessPayment.VerificationMethod.PROVIDER, payment.getVerificationMethod());
        assertEquals(BusinessPayment.PaymentMethod.ONLINE, payment.getPaymentMethod());
        assertNotNull(payment.getVerifiedAt());
        assertNotNull(payment.getCreatedAt());
        assertNotNull(payment.getUpdatedAt());
    }

    @Test
    void explicitManualEvidenceAndExistingVerificationArePreserved() {
        BusinessPayment payment = new BusinessPayment();
        Instant verified = Instant.now().minusSeconds(30);
        payment.setStatus(BusinessPayment.Status.PENDING);
        payment.setVerificationMethod(BusinessPayment.VerificationMethod.MANUAL_BUSINESS);
        payment.setPaymentMethod(BusinessPayment.PaymentMethod.CASH);
        payment.setVerifiedAt(verified);

        payment.prePersist();

        assertEquals(BusinessPayment.VerificationMethod.MANUAL_BUSINESS, payment.getVerificationMethod());
        assertEquals(BusinessPayment.PaymentMethod.CASH, payment.getPaymentMethod());
        assertEquals(verified, payment.getVerifiedAt());
    }

    @Test
    void succeededPaymentDoesNotOverwriteExistingVerifiedTimestamp() {
        BusinessPayment payment = new BusinessPayment();
        Instant verified = Instant.now().minusSeconds(60);
        payment.setStatus(BusinessPayment.Status.SUCCEEDED);
        payment.setVerifiedAt(verified);

        payment.prePersist();

        assertEquals(verified, payment.getVerifiedAt());
    }
}
