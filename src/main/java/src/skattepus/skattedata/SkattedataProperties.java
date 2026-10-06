package src.skattepus.skattedata;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tilgjengelege Skatteetaten-API for testing. Kvar oppføring har sin eigen
 * Maskinporten-scope (rettigheitspakke), base-URL og stiltemplate med
 * `{placeholder}`-segment som testkontrollaren fyller ut.
 *
 * @param apis kart over API-nytsel (t.d. "mvafastsetting") til konfigurasjon
 */
@ConfigurationProperties(prefix = "skattepus.skatteetaten")
public record SkattedataProperties(Map<String, ApiConfig> apis) {

    public SkattedataProperties {
        if (apis == null) {
            apis = new LinkedHashMap<>();
        }
    }

    /**
     * Konfigurasjon for eitt Skatteetaten-API.
     *
     * @param scope        Maskinporten-scope for API-et, t.d. {@code skatteetaten:mvafastsetting}
     * @param baseUrl      base-URL til API-et, t.d. {@code https://mvafastsetting.api.skatteetaten-test.no/v1}
     * @param pathTemplate stiltemplate med {placeholder}-segment, t.d. {@code /{rettighetspakke}/fastsettinger/{organisasjonsnummer}}
     */
    public record ApiConfig(String scope, String baseUrl, String pathTemplate) {
    }
}
