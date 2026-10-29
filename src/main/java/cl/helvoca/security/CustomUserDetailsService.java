package cl.helvoca.security;

import cl.helvoca.user.AppUserRepository;
import cl.helvoca.auth.BusinessAccountVerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final AppUserRepository users;
    private BusinessAccountVerificationService verification;

    public CustomUserDetailsService(AppUserRepository users) { this.users = users; }

    @Autowired(required = false)
    void setAccountVerification(BusinessAccountVerificationService verification) { this.verification = verification; }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = users.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        if (verification != null && verification.isRequired() && user.getBusiness() != null
                && !verification.isVerified(user.getBusiness().getId(), user.getId())) {
            throw new UsernameNotFoundException("User not found");
        }

        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getCode().name()))
                .toList();

        return User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .disabled(!user.isActive())
                .build();
    }
}
