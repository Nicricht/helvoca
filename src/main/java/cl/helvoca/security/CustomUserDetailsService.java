package cl.helvoca.security;

import cl.helvoca.user.AppUserRepository;
import cl.helvoca.auth.BusinessAccountMailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final AppUserRepository users;
    private BusinessAccountMailService mail;

    public CustomUserDetailsService(AppUserRepository users) { this.users = users; }

    @Autowired(required = false)
    void setAccountMail(BusinessAccountMailService mail) { this.mail = mail; }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = users.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        if (mail != null && mail.isVerificationRequired() && user.getBusiness() != null
                && user.getEmailVerifiedAt() == null) {
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
