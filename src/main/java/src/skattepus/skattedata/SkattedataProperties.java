package src.skattepus.skattedata;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Available Skatteetaten APIs for testing. Each entry has its own Maskinporten
 * scope, base URL and path template with `{placeholder}` segments that the test
 * controller fills in.
 *
 * @param apis map from API name (e.g. "mvafastsetting") to configuration
 */
@ConfigurationProperties(prefix = "skattepus.skatteetaten")
public record SkattedataProperties(Map<String, ApiConfig> apis) {

    public SkattedataProperties {
        if (apis == null) {
            apis = new LinkedHashMap<>();
        }
    }

    /**
     * Configuration for one Skatteetaten API.
     *
     * @param scope        Maskinporten scope for the API, e.g. {@code skatteetaten:mvafastsetting}
     * @param baseUrl      base URL of the API, e.g. {@code https://mvafastsetting.api.skatteetaten-test.no/v1}
     * @param pathTemplate path template with {placeholder} segments, e.g. {@code /{rettighetspakke}/fastsettinger/{organisasjonsnummer}}
     */
    public record ApiConfig(String scope, String baseUrl, String pathTemplate) {
    }
}
