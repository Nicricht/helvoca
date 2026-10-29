package cl.helvoca.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Testcontainers
@SpringBootTest
class BusinessAccountVerificationPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
    }

    @Autowired @Qualifier("migrationDataSource") DataSource ownerSource;
    @Autowired WebApplicationContext web;
    @Autowired PasswordEncoder passwords;
    @Autowired BusinessAccountTokenService tokens;
    @MockitoBean BusinessAccountMailService mail;

    private JdbcTemplate owner;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        owner = new JdbcTemplate(ownerSource);
        owner.update("DELETE FROM business_account_token");
        owner.update("DELETE FROM api_rate_limit_bucket");
        mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        reset(mail);
        when(mail.isEnabled()).thenReturn(true);
        when(mail.isVerificationRequired()).thenReturn(true);
    }

    @Test
    void optInSignupReturnsNoJwtUntilOneUseEmailConfirmation() throws Exception {
        String address = email();
        String registration = """
                {"adminName":"Ana","businessName":"Clínica Norte","email":"%s",
                 "password":"initial-password-123","timezone":"America/Santiago","language":"es"}
                """.formatted(address);
        var registered = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registration))
                .andReturn();
        assertEquals(202, registered.getResponse().getStatus());
        assertFalse(registered.getResponse().getContentAsString().contains("accessToken"));
        assertFalse(registered.getResponse().getContentAsString().contains("Bearer"));

        UUID user = owner.queryForObject("SELECT id FROM app_user WHERE email=?", UUID.class, address);
        assertNull(owner.queryForObject("SELECT email_verified_at FROM app_user WHERE id=?", java.time.Instant.class, user));
        var captured = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mail).sendVerification(eq(address), captured.capture());

        String credentials = "{\"email\":\"" + address + "\",\"password\":\"initial-password-123\"}";
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(result -> assertEquals(401, result.getResponse().getStatus()));

        String confirmation = "{\"token\":\"" + captured.getValue() + "\"}";
        mvc.perform(post("/api/v1/auth/email/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(confirmation))
                .andExpect(result -> assertEquals(204, result.getResponse().getStatus()));
        assertNotNull(owner.queryForObject("SELECT email_verified_at FROM app_user WHERE id=?", java.time.Instant.class, user));
        var session = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andReturn();
        assertEquals(200, session.getResponse().getStatus());
        assertTrue(session.getResponse().getContentAsString().contains("accessToken"));
        mvc.perform(post("/api/v1/auth/email/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(confirmation))
                .andExpect(result -> assertEquals(400, result.getResponse().getStatus()));
    }

    @Test
    void defaultOffSignupStillPersistsLoginEligibleOwnerAndReturnsJwt() throws Exception {
        when(mail.isEnabled()).thenReturn(false);
        when(mail.isVerificationRequired()).thenReturn(false);
        String address = email();
        String registration = """
                {"adminName":"Ana","businessName":"Negocio sin correo saliente","email":"%s",
                 "password":"initial-password-123","timezone":"America/Santiago","language":"es"}
                """.formatted(address);
        var response = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registration))
                .andReturn();
        assertEquals(200, response.getResponse().getStatus());
        assertTrue(response.getResponse().getContentAsString().contains("accessToken"));
        UUID user = owner.queryForObject("SELECT id FROM app_user WHERE email=?", UUID.class, address);
        assertNotNull(owner.queryForObject(
                "SELECT email_verified_at FROM app_user WHERE id=?", java.time.Instant.class, user),
                "default-off registration must not be locked out after later verification enablement");
        verify(mail, never()).sendVerification(anyString(), anyString());
    }

    @Test
    void emailResendNeverEnumeratesUnknownInactivePlatformOrAlreadyVerifiedUsers() throws Exception {
        UUID business = business();
        String inactive = email();
        user(business, inactive, false, false);
        String verified = email();
        user(business, verified, true, true);
        String platform = email();
        user(null, platform, true, false);

        for (String address : new String[] { email(), inactive, verified, platform }) {
            mvc.perform(post("/api/v1/auth/email/resend")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + address + "\"}"))
                    .andExpect(result -> assertEquals(202, result.getResponse().getStatus()));
        }
        verify(mail, never()).sendVerification(anyString(), anyString());
        assertEquals(0L, owner.queryForObject("SELECT count(*) FROM business_account_token", Long.class));
    }

    @Test
    void resendIssuesVerificationOnlyForUnverifiedTenantAccountAndRejectsResetTokens() throws Exception {
        UUID business = business();
        String address = email();
        UUID user = user(business, address, true, false);
        String resetToken = tokens.issue(business, user, BusinessAccountTokenService.Purpose.RESET).token();

        mvc.perform(post("/api/v1/auth/email/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + resetToken + "\"}"))
                .andExpect(result -> assertEquals(400, result.getResponse().getStatus()));
        mvc.perform(post("/api/v1/auth/email/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + address + "\"}"))
                .andExpect(result -> assertEquals(202, result.getResponse().getStatus()));
        var captured = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mail).sendVerification(eq(address), captured.capture());
        mvc.perform(post("/api/v1/auth/email/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + captured.getValue() + "\"}"))
                .andExpect(result -> assertEquals(204, result.getResponse().getStatus()));
        assertNotNull(owner.queryForObject("SELECT email_verified_at FROM app_user WHERE id=?", java.time.Instant.class, user));
    }

    @Test
    void verifiedLegacyBusinessAndTenantlessPlatformUserKeepTheirLogin() throws Exception {
        UUID business = business();
        String legacy = email();
        user(business, legacy, true, true);
        String platform = email();
        user(null, platform, true, false);

        for (String address : new String[] { legacy, platform }) {
            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + address
                                    + "\",\"password\":\"initial-password-123\"}"))
                    .andExpect(result -> assertEquals(200, result.getResponse().getStatus()));
        }
    }

    private UUID business() {
        UUID id = UUID.randomUUID();
        owner.update("INSERT INTO business(id,name) VALUES (?,?)", id, "Verified company " + id);
        return id;
    }

    private UUID user(UUID business, String address, boolean active, boolean verified) {
        UUID id = UUID.randomUUID();
        owner.update("INSERT INTO app_user(id,business_id,name,email,password_hash,active,email_verified_at) "
                        + "VALUES (?,?,?,?,?,?,?)", id, business, "Email user", address,
                passwords.encode("initial-password-123"), active,
                verified ? java.sql.Timestamp.from(java.time.Instant.now()) : null);
        return id;
    }

    private static String email() {
        return "verify-" + UUID.randomUUID() + "@example.test";
    }
}
