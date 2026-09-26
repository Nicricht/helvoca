package cl.helvoca.user;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TeamInvitationAdminControllerContractTest {
    @Test
    void invitationManagementIsBusinessAdminOnly() {
        RequestMapping mapping = TeamInvitationAdminController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertTrue(Arrays.asList(mapping.value()).contains("/api/v1/admin/invitations"));

        PreAuthorize auth = TeamInvitationAdminController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(auth);
        assertEquals("hasRole('BUSINESS_ADMIN')", auth.value());
    }
}
