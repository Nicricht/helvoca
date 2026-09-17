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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    static final String[] PUBLIC_CONSOLE_ASSETS = {
            "/", "/index.html", "/app.js", "/phone-provisioning.js", "/commercial-status.js",
            "/voice-selector.js", "/ux-simplification.js", "/styles.css",
            "/sales.html", "/sales.css",
            "/pricing.html", "/pricing.js", "/pricing.css",
            "/operations.html", "/operations.js", "/operations.css",
            "/conversations.html", "/conversations.js", "/conversations.css",
            "/simulator.html", "/simulator.js", "/simulator.css",
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
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        scopes.setAuthoritiesClaimName("roles");
        scopes.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(scopes);
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
                                "/api/v1/public/pricing", "/actuator/health").permitAll()
                        .requestMatchers("/webhooks/v1/twilio/**", "/webhooks/v1/openai/**",
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
