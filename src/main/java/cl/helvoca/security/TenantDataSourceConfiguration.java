package cl.helvoca.security;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

/**
 * Keeps Flyway on the database owner connection while all application/JPA/JDBC
 * work uses the tenant-aware runtime wrapper.
 */
@Configuration
public class TenantDataSourceConfiguration {

    @Bean(name = "migrationDataSource", destroyMethod = "close")
    @FlywayDataSource
    HikariDataSource migrationDataSource(Environment environment) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(required(environment, "spring.datasource.url"));
        config.setUsername(required(environment, "spring.datasource.username"));
        config.setPassword(environment.getProperty("spring.datasource.password", ""));
        config.setPoolName("helvoca-postgres");
        config.setMaximumPoolSize(integer(environment, "spring.datasource.hikari.maximum-pool-size", 10));
        config.setMinimumIdle(integer(environment, "spring.datasource.hikari.minimum-idle", 1));
        config.setConnectionTimeout(longValue(environment, "spring.datasource.hikari.connection-timeout", 30_000L));
        return new HikariDataSource(config);
    }

    @Bean(name = "dataSource")
    @Primary
    DataSource dataSource(@Qualifier("migrationDataSource") HikariDataSource raw,
                          TenantDatabaseContext context) {
        return new TenantAwareDataSource(raw, context);
    }

    private static String required(Environment environment, String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing required property " + key);
        return value;
    }

    private static int integer(Environment environment, String key, int fallback) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) return fallback;
        return Integer.parseInt(value.trim());
    }

    private static long longValue(Environment environment, String key, long fallback) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) return fallback;
        return Long.parseLong(value.trim());
    }
}
