package cl.helvoca.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import cl.helvoca.user.RoleCode;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    static final String[] PUBLIC_CONSOLE_ASSETS = {
            "/", "/index.html", "/app.js", "/phone-provisioning.js", "/commercial-status.js",
            "/voice-selector.js", "/ux-simplification.js", "/first-user-ux-v2.js", "/first-user-ux-v2.css",
            "/styles.css", "/frontend-foundation.css", "/commercial-ui-v3.css", "/auth-visual-refresh.css", "/landing-motion.css", "/recepvoz-auth-hero.svg", "/recepvoz-phone-hero.svg", "/dashboard-finish.css", "/dashboard-motion.css",
            "/business-activation-guide.js", "/pilot-readiness.js", "/pilot-control.js", "/pilot-preflight.js", "/pilot-metrics.js",
            "/pilot-activation.js", "/schedule-exceptions.js", "/payment-sandbox-onboarding.js",
            "/invite.html", "/invite.js", "/team-invitations.js",
            "/platform.html", "/platform.js", "/operations.html", "/operations.js",
            "/sales.html", "/sales.css",
            "/privacy.html", "/terms.html",
            "/pricing.html", "/pricing.js", "/pricing.css",
            "/app/**",
            "/account.html", "/account.js", "/account.css",
            "/settings.html", "/settings-page.js",
            "/inventory.html", "/inventory.js", "/inventory.css",
            "/simulator.html", "/simulator.js", "/simulator.css",
            "/conversations.html", "/conversations.js", "/conversations.css",
            "/manifest.webmanifest", "/service-worker.js", "/recepvoz-icon-192.png", "/recepvoz-icon-512.png",
            "/favicon.ico", "/error"
    };

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    DaoAuthenticationProvider authenticationProvider(CustomUserDetailsService userDetailsService,
                                                     PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    AuthenticationManager authenticationManager(DaoAuthenticationProvider provider) {
        return provider::authenticate;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");

        JwtGrantedAuthoritiesConverter permissions = new JwtGrantedAuthoritiesConverter();
        permissions.setAuthoritiesClaimName("permissions");
        permissions.setAuthorityPrefix("PERM_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<GrantedAuthority> combined = new LinkedHashSet<>();
            Collection<GrantedAuthority> roleAuthorities = roles.convert(jwt);
            Collection<GrantedAuthority> permissionAuthorities = permissions.convert(jwt);
            if (roleAuthorities != null) combined.addAll(roleAuthorities);
            if (permissionAuthorities != null) combined.addAll(permissionAuthorities);

            var roleClaims = jwt.getClaimAsStringList("roles");
            if (roleClaims != null) {
                java.util.Set<RoleCode> roleCodes = new java.util.HashSet<>();
                for (String value : roleClaims) {
                    try {
                        roleCodes.add(RoleCode.valueOf(value));
                    } catch (IllegalArgumentException ignored) {
                        // Unknown future roles do not gain implicit permissions.
                    }
                }
                RolePermissionCatalog.permissionsFor(roleCodes).forEach(permission ->
                        combined.add(new SimpleGrantedAuthority("PERM_" + permission.name())));
            }
            return combined;
        });
        return converter;
    }

    /**
     * These filters are components because they have injected dependencies, but
     * they must execute only inside Spring Security after bearer authentication.
     * Disable servlet-container auto registration to avoid OncePerRequestFilter
     * consuming its marker before the authenticated security-chain position.
     */
    @Bean
    FilterRegistrationBean<TenantDatabaseContextFilter> tenantDatabaseContextFilterRegistration(
            TenantDatabaseContextFilter filter) {
        FilterRegistrationBean<TenantDatabaseContextFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<ApiRateLimitFilter> apiRateLimitFilterRegistration(ApiRateLimitFilter filter) {
        FilterRegistrationBean<ApiRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationConverter jwtConverter,
                                            TenantDatabaseContextFilter tenantDatabaseContextFilter,
                                            ApiRateLimitFilter apiRateLimitFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_CONSOLE_ASSETS).permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/register",
                                "/api/v1/auth/invitations/**",
                                "/api/v1/public/pricing", "/actuator/health").permitAll()
                        .requestMatchers("/webhooks/v1/twilio/**", "/webhooks/v1/meta/**", "/webhooks/v1/openai/**",
                                "/webhooks/v1/mercadopago", "/webhooks/v1/payments/**").permitAll()
                        .requestMatchers("/ws/v1/twilio/**").permitAll()
                        .requestMatchers("/api/v1/platform/**").hasRole("PLATFORM_ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .addFilterAfter(tenantDatabaseContextFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(apiRateLimitFilter, TenantDatabaseContextFilter.class);
        return http.build();
    }
}
