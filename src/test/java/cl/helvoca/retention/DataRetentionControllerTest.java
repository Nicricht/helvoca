package cl.helvoca.retention;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.*;

class DataRetentionControllerTest {

    @Test
    void retentionInventoryIsBusinessAdminOnly() {
        PreAuthorize rule = DataRetentionController.class.getAnnotation(PreAuthorize.class);

        assertNotNull(rule);
        assertEquals("hasRole('BUSINESS_ADMIN')", rule.value());
    }
}
