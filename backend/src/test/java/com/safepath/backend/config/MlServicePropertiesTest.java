package com.safepath.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MlServicePropertiesTest {

    @Test
    @DisplayName("MlServiceProperties has secure, documented default configuration")
    void testDefaultValues() {
        MlServiceProperties properties = new MlServiceProperties();

        assertThat(properties.getUrl()).isEqualTo("http://localhost:8001");
        assertThat(properties.getTimeoutMs()).isEqualTo(5000);
        assertThat(properties.getConnectTimeoutMs()).isEqualTo(3000);
        assertThat(properties.getMaxBatchSize()).isEqualTo(500);
        assertThat(properties.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("MlServiceProperties binds custom environment properties correctly")
    void testCustomBinding() {
        Map<String, Object> map = new HashMap<>();
        map.put("safepath.ml-service.url", "http://ml-internal:8001");
        map.put("safepath.ml-service.timeout-ms", "8000");
        map.put("safepath.ml-service.connect-timeout-ms", "4000");
        map.put("safepath.ml-service.max-batch-size", "250");
        map.put("safepath.ml-service.enabled", "false");

        ConfigurationPropertySource source = new MapConfigurationPropertySource(map);
        Binder binder = new Binder(source);

        MlServiceProperties properties = new MlServiceProperties();
        binder.bind("safepath.ml-service", Bindable.ofInstance(properties));

        assertThat(properties.getUrl()).isEqualTo("http://ml-internal:8001");
        assertThat(properties.getTimeoutMs()).isEqualTo(8000);
        assertThat(properties.getConnectTimeoutMs()).isEqualTo(4000);
        assertThat(properties.getMaxBatchSize()).isEqualTo(250);
        assertThat(properties.isEnabled()).isFalse();
    }
}
