package cl.helvoca.bootstrap;

import cl.helvoca.billing.BusinessSubscriptionService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.user.AppUser;
import cl.helvoca.user.AppUserRepository;
import cl.helvoca.user.Role;
import cl.helvoca.user.RoleCode;
import cl.helvoca.user.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DevDataInitializerTest {

    @Test
    void createsAnIsolatedCommercialDemoTenantWithTrialWhenEnabled() throws Exception {
        BusinessRepository businesses = mock(BusinessRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        BusinessSubscriptionService subscriptions = mock(BusinessSubscriptionService.class);

        UUID businessId = UUID.randomUUID();
        when(users.existsByEmailIgnoreCase("demo@helvoca.local")).thenReturn(false);
        when(businesses.saveAndFlush(any(Business.class))).thenAnswer(invocation -> {
            Business business = invocation.getArgument(0);
            ReflectionTestUtils.setField(business, "id", businessId);
            return business;
        });
        Role adminRole = new Role();
        adminRole.setCode(RoleCode.BUSINESS_ADMIN);
        adminRole.setName("Business admin");
        when(roles.findByCode(RoleCode.BUSINESS_ADMIN)).thenReturn(Optional.of(adminRole));
        when(encoder.encode("safe-demo-password")).thenReturn("hashed");

        DevDataInitializer initializer = new DevDataInitializer(businesses, users, roles, encoder);
        initializer.setSubscriptions(subscriptions);
        ReflectionTestUtils.setField(initializer, "enabled", true);
        ReflectionTestUtils.setField(initializer, "adminEmail", "demo@helvoca.local");
        ReflectionTestUtils.setField(initializer, "adminPassword", "safe-demo-password");

        initializer.run();

        var businessCaptor = org.mockito.ArgumentCaptor.forClass(Business.class);
        verify(businesses).saveAndFlush(businessCaptor.capture());
        Business business = businessCaptor.getValue();
        assertEquals("Helvoca Demo Business", business.getName());
        assertEquals("America/Santiago", business.getTimezone());
        assertEquals("es", business.getLanguage());
        assertNull(business.getHumanTransferPhone());
        verify(subscriptions).startBasicTrial(businessId);

        var userCaptor = org.mockito.ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(userCaptor.capture());
        AppUser admin = userCaptor.getValue();
        assertSame(business, admin.getBusiness());
        assertEquals("Demo Administrator", admin.getName());
        assertEquals("demo@helvoca.local", admin.getEmail());
        assertEquals("hashed", admin.getPasswordHash());
        assertTrue(admin.getRoles().contains(adminRole));
    }

    @Test
    void doesNothingWhenSeedIsDisabled() throws Exception {
        BusinessRepository businesses = mock(BusinessRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        DevDataInitializer initializer = new DevDataInitializer(businesses, users, roles, encoder);
        ReflectionTestUtils.setField(initializer, "enabled", false);

        initializer.run();

        verifyNoInteractions(businesses, users, roles, encoder);
    }
}
