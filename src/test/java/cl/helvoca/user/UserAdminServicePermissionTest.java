package cl.helvoca.user;

import cl.helvoca.audit.AuditService;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.security.TenantProvider;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserAdminServicePermissionTest {

    @Test
    void createsKitchenUserButRejectsOwnerAssignment() {
        AppUserRepository users = mock(AppUserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        BusinessRepository businesses = mock(BusinessRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        TenantProvider tenant = mock(TenantProvider.class);
        AuditService audit = mock(AuditService.class);
        UUID businessId = UUID.randomUUID();

        when(tenant.requireBusinessId()).thenReturn(businessId);
        Business business = mock(Business.class);
        when(businesses.findById(businessId)).thenReturn(Optional.of(business));
        when(users.existsByEmailIgnoreCase("chef@example.cl")).thenReturn(false);
        when(encoder.encode("password123")).thenReturn("hash");
        Role kitchen = new Role();
        kitchen.setCode(RoleCode.KITCHEN);
        kitchen.setName("Kitchen");
        when(roles.findByCode(RoleCode.KITCHEN)).thenReturn(Optional.of(kitchen));
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));

        UserAdminService service = new UserAdminService(
                users, roles, businesses, encoder, tenant, audit);

        UserResponse created = service.create(new CreateUserRequest(
                "Chef", "chef@example.cl", "password123", Set.of(RoleCode.KITCHEN)));
        assertNotNull(created);

        assertThrows(IllegalArgumentException.class, () -> service.create(new CreateUserRequest(
                "Owner", "owner@example.cl", "password123", Set.of(RoleCode.BUSINESS_OWNER))));
    }
}
