package cl.helvoca.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentationExposureConfigTest {

    @Test
    void documentationEndpointsAreDisabledByDefault() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();
        assertNotNull(properties);

        String apiDocs = properties.getProperty("springdoc.api-docs.enabled");
        String swaggerUi = properties.getProperty("springdoc.swagger-ui.enabled");
        assertNotNull(apiDocs);
        assertNotNull(swaggerUi);
        assertTrue(apiDocs.endsWith(":false}"));
        assertTrue(swaggerUi.endsWith(":false}"));
    }
}
