package com.dynamis.sep_api.shared.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityConfigurationTest {

    @Test
    void profileProdRestringeManagementEConfiguraDiretorioDeLog() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-prod.yml"));
        Properties properties = yaml.getObject();

        assertThat(properties)
                .isNotNull()
                .containsEntry("management.server.address", "${MANAGEMENT_ADDRESS:127.0.0.1}")
                .containsEntry("management.server.port", "${MANAGEMENT_PORT:8081}")
                .containsEntry("logging.file.path", "${LOG_PATH:/var/log/sep-api}");
    }

    @Test
    void logbackProdUsaJsonRotativoEListaBrancaDeMdc() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        String xml = new String(
                new ClassPathResource("logback-spring.xml").getInputStream().readAllBytes());

        factory.newDocumentBuilder().parse(new ClassPathResource("logback-spring.xml").getInputStream());

        assertThat(xml)
                .contains("LoggingEventCompositeJsonEncoder")
                .contains("<includeMdcKeyName>correlationId</includeMdcKeyName>")
                .contains("<maxHistory>7</maxHistory>")
                .doesNotContain("<mdc/>");
    }
}
