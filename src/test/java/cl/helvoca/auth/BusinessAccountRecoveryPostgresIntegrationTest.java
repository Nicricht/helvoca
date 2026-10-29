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

@SpringBootTest
@Testcontainers
class BusinessAccountRecoveryPostgresIntegrationTest {
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
        // MockMvc uses the same loopback IP in every test. Each case gets a
        // fresh distributed counter; production IP throttling remains active.
        owner.update("DELETE FROM api_rate_limit_bucket");
        mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        reset(mail);
    }

    @Test
    void unknownInactiveAndPlatformIdentitiesReceiveTheSame202WithoutSendingOrIssuing() throws Exception {
        when(mail.isEnabled()).thenReturn(true);
        UUID business = business();
        String inactive = email();
        account(business, inactive, false);
        String platform = email();
        account(null, platform, true);

        for (String address : new String[] { email(), inactive, platform }) {
            mvc.perform(post("/api/v1/auth/password-reset/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + address + "\"}"))
                    .andExpect(result -> assertEquals(202, result.getResponse().getStatus()))
                    .andExpect(result -> assertFalse(result.getResponse().getContentAsString().contains(address)));
        }
        verify(mail, never()).sendPasswordReset(anyString(), anyString());
        assertEquals(0L, owner.queryForObject("SELECT count(*) FROM business_account_token", Long.class));
    }

    @Test
    void activeBusinessAccountReceivesOnlyEmailAndRedeemsBcryptPasswordExactlyOnce() throws Exception {
        when(mail.isEnabled()).thenReturn(true);
        UUID business = business();
        UUID user = account(business, email(), true);
        String address = owner.queryForObject("SELECT email FROM app_user WHERE id=?", String.class, user);

        var requested = mvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + address.toUpperCase() + "\"}"))
                .andReturn();
        assertEquals(202, requested.getResponse().getStatus());
        var raw = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mail).sendPasswordReset(eq(address), raw.capture());
        assertEquals(43, raw.getValue().length());
        assertFalse(requested.getResponse().getContentAsString().contains(raw.getValue()));
        assertEquals(1L, owner.queryForObject("SELECT count(*) FROM business_account_token", Long.class));

        String body = "{\"token\":\"" + raw.getValue() + "\",\"newPassword\":\"a-new-password-123\"}";
        mvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(result -> assertEquals(204, result.getResponse().getStatus()));
        String hash = owner.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, user);
        assertTrue(passwords.matches("a-new-password-123", hash));
        assertFalse(passwords.matches("old-password-123", hash));
        mvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(result -> assertEquals(400, result.getResponse().getStatus()));
    }

    @Test
    void disabledMailNeverIssuesTokenEvenForKnownBusinessAccount() throws Exception {
        UUID business = business();
        UUID user = account(business, email(), true);
        String address = owner.queryForObject("SELECT email FROM app_user WHERE id=?", String.class, user);
        mvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + address + "\"}"))
                .andExpect(result -> assertEquals(202, result.getResponse().getStatus()));
        verify(mail, never()).sendPasswordReset(anyString(), anyString());
        assertEquals(0L, owner.queryForObject("SELECT count(*) FROM business_account_token", Long.class));
    }

    @Test
    void smtpFailureNeverRevealsWhetherAnEligibleBusinessAccountExists() throws Exception {
        when(mail.isEnabled()).thenReturn(true);
        UUID business = business();
        UUID user = account(business, email(), true);
        String address = owner.queryForObject("SELECT email FROM app_user WHERE id=?", String.class, user);
        doThrow(new IllegalStateException("Simulated SMTP outage"))
                .when(mail).sendPasswordReset(eq(address), anyString());

        var response = mvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + address + "\"}"))
                .andReturn();
        assertEquals(202, response.getResponse().getStatus());
        assertFalse(response.getResponse().getContentAsString().contains(address));
        assertEquals(1L, owner.queryForObject("SELECT count(*) FROM business_account_token", Long.class));
    }

    @Test
    void wrongPurposeAndPasswordValidationCannotChangeCredentialsOrConsumeValidToken() throws Exception {
        UUID business = business();
        UUID user = account(business, email(), true);
        String original = owner.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, user);
        String verifyToken = tokens.issue(business, user, BusinessAccountTokenService.Purpose.VERIFY).token();
        String resetToken = tokens.issue(business, user, BusinessAccountTokenService.Purpose.RESET).token();

        for (String raw : new String[] { "wrong", verifyToken, resetToken }) {
            String password = raw.equals(resetToken) ? "short" : "a-new-password-123";
            String body = "{\"token\":\"" + raw + "\",\"newPassword\":\"" + password + "\"}";
            mvc.perform(post("/api/v1/auth/password-reset/confirm")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(result -> assertEquals(400, result.getResponse().getStatus()));
        }
        assertEquals(original, owner.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, user));
        mvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + resetToken + "\",\"newPassword\":\"valid-password-321\"}"))
                .andExpect(result -> assertEquals(204, result.getResponse().getStatus()));
        assertTrue(passwords.matches("valid-password-321", owner.queryForObject(
                "SELECT password_hash FROM app_user WHERE id=?", String.class, user)));
    }

    private UUID business() {
        UUID id = UUID.randomUUID();
        owner.update("INSERT INTO business(id,name) VALUES (?,?)", id, "Recovery company " + id);
        return id;
    }

    private UUID account(UUID businessId, String email, boolean active) {
        UUID id = UUID.randomUUID();
        owner.update("INSERT INTO app_user(id,business_id,name,email,password_hash,active) VALUES (?,?,?,?,?,?)",
                id, businessId, "Recovery user", email, passwords.encode("old-password-123"), active);
        return id;
    }

    private static String email() {
        return "recover-" + UUID.randomUUID() + "@example.test";
    }
}
