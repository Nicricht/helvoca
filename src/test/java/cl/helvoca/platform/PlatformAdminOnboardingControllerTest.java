package cl.helvoca.platform;

import org.junit.jupiter.api.Test;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformAdminOnboardingControllerTest {
    @Test
    void platformInviteControllerSupportsClassBasedSecurityAdviceWhenEnabled() {
        PlatformAdminInviteService service = mock(PlatformAdminInviteService.class);
        ProxyFactory proxyFactory = new ProxyFactory(new PlatformAdminInviteController(service));
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice((MethodInterceptor) invocation -> {
            if ("invite".equals(invocation.getMethod().getName())) {
                throw new AccessDeniedException("Authorization required");
            }
            return invocation.proceed();
        });

        PlatformAdminInviteController secured = (PlatformAdminInviteController) proxyFactory.getProxy();
        var attempt = new PlatformAdminInviteController.InvitationRequest("Another Admin", "admin@example.test");
        assertThrows(AccessDeniedException.class, () -> secured.invite(attempt));
        verifyNoInteractions(service);
    }

    @Test
    void invitationAndRevocationUseOnlyAuthenticatedServiceWithNoStoreResponse() {
        PlatformAdminInviteService service = mock(PlatformAdminInviteService.class);
        PlatformAdminInviteController controller = new PlatformAdminInviteController(service);
        UUID id = UUID.randomUUID();
        var view = new PlatformAdminInviteService.InviteView(id, "t".repeat(43), Instant.now().plusSeconds(3600));
        when(service.invite("Second Person", "second@example.test")).thenReturn(view);
        var request = new PlatformAdminInviteController.InvitationRequest("Second Person", "second@example.test");
        ResponseEntity<PlatformAdminInviteService.InviteView> response = controller.invite(request);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(view, response.getBody());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        controller.revoke(id);
        verify(service).invite("Second Person", "second@example.test");
        verify(service).revoke(id);
        verifyNoMoreInteractions(service);
    }

    @Test
    void acceptanceHandsUserChosenSecretToServiceAndNeverAuthenticatesAutomatically() {
        PlatformAdminInviteService service = mock(PlatformAdminInviteService.class);
        var controller = new PlatformAdminInviteAcceptanceController(service);
        String token = "z".repeat(43);
        String privatePassword = "IndependentlyChosenPassword123";
        controller.accept(new PlatformAdminInviteAcceptanceController.Acceptance(token, privatePassword));
        verify(service).accept(token, privatePassword);
        verifyNoMoreInteractions(service);
    }

    @Test
    void operatorBootstrapOnlyRequestsAnInvitationNotAnAdministratorSession() {
        PlatformAdminInviteService service = mock(PlatformAdminInviteService.class);
        String token = "q".repeat(43);
        var runner = new PlatformAdminBootstrapRunner(service, "First Person", "first@example.test", token);
        runner.run();
        verify(service).bootstrap("First Person", "first@example.test", token);
        verifyNoMoreInteractions(service);
    }
}
