package cl.helvoca.telephony.twilio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;
import java.util.regex.Pattern;

/**
 * Fail-closed one-shot gate in front of the certification startup runner.
 * A unique run id must be claimed atomically before the existing runner is
 * allowed to execute. Reusing the token after a restart or on another replica
 * is rejected by PostgreSQL.
 */
@Component
public class TwilioCertificationStartupRunGate implements BeanPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(TwilioCertificationStartupRunGate.class);
    private static final Pattern RUN_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$");

    private final boolean enabled;
    private final String runId;
    private final String direction;
    private final JdbcTemplate jdbcTemplate;

    public TwilioCertificationStartupRunGate(
            @Value("${TWILIO_CERTIFICATION_CALL_ON_STARTUP:false}") boolean enabled,
            @Value("${TWILIO_CERTIFICATION_RUN_ID:}") String runId,
            @Value("${TWILIO_CERTIFICATION_DIRECTION:outbound-test}") String direction,
            JdbcTemplate jdbcTemplate) {
        this.enabled = enabled;
        this.runId = runId;
        this.direction = TwilioCertificationStartupRunner.normalizeDirection(direction);
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof TwilioCertificationStartupRunner runner)) return bean;

        return Proxy.newProxyInstance(
                bean.getClass().getClassLoader(),
                new Class<?>[]{ApplicationRunner.class},
                (proxy, method, args) -> {
                    if ("run".equals(method.getName())) {
                        if (!authorizeOnce()) return null;
                        return method.invoke(runner, args);
                    }
                    return method.invoke(runner, args);
                });
    }

    boolean authorizeOnce() {
        if (!enabled) return false;
        if (!validRunId(runId)) {
            log.warn("TWILIO_CERTIFICATION_CALL blocked: TWILIO_CERTIFICATION_RUN_ID is missing or invalid");
            return false;
        }

        try {
            int inserted = jdbcTemplate.update("""
                    INSERT INTO twilio_certification_run (run_id, direction, claimed_at)
                    VALUES (?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (run_id) DO NOTHING
                    """, runId.trim(), direction);
            if (inserted != 1) {
                log.info("TWILIO_CERTIFICATION_CALL blocked: run token already consumed");
                return false;
            }
            log.info("TWILIO_CERTIFICATION_CALL authorized for one startup execution");
            return true;
        } catch (Exception e) {
            log.error("TWILIO_CERTIFICATION_CALL blocked: run token claim failed");
            return false;
        }
    }

    static boolean validRunId(String value) {
        return value != null && RUN_ID.matcher(value.trim()).matches();
    }
}
