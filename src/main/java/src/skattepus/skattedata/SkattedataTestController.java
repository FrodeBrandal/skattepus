package src.skattepus.skattedata;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Testkontrollar for å verifisere tilgang til Skatteetaten sine API via Maskinporten.
 * Stiltemplate til kvart API ({@code skattepus.skatteetaten.apis.*}) blir fylt ut med
 * parameter du sendar inn; parameter som ikkje inngår i stien, blir query-parameter.
 */
@RestController
@RequestMapping("/skattedata")
public class SkattedataTestController {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]+)}");

    private final SkattedataProperties properties;
    private final SkattedataClient client;

    /**
     * Constructs the controller.
     *
     * @param properties konfigurerte Skatteetaten-API
     * @param client     Bearer-token-klient som utfører dei eigentlege kalla
     */
    public SkattedataTestController(SkattedataProperties properties, SkattedataClient client) {
        this.properties = properties;
        this.client = client;
    }

    /**
     * Listar dei konfigurerte API-enda med scope, base-URL og stiltemplate.
     *
     * @return kart over API-nytsel til konfigurasjon
     */
    @GetMapping("/apis")
    public Map<String, SkattedataProperties.ApiConfig> apis() {
        return properties.apis();
    }

    /**
     * Utfører eit test-kall mot det valde API-et. Parameter som svarer til ein
     * {placeholder} i stiltemplateet blir sett inn i stien (uttom URL-koding),
     * resten blir sende som query-parameter.
     *
     * @param api   navn på API-et, sjå {@code /skattedata/apis}
     * @param params parameter for stiltemplateet og eventuelle query-parameter
     * @return statuskode, rå respons-body og Korrelasjonsid; ved feil (t.d. avvist
     *         Maskinporten-token) returneres status "FEIL" med rotårsaka
     * @throws IllegalArgumentException viss API-et ikkje er konfigurert
     * @throws IllegalStateException    viss obligatoriske plasesholdarar manglar
     */
    @GetMapping("/{api}")
    public Map<String, Object> call(@PathVariable String api, @RequestParam Map<String, String> params) {
        SkattedataProperties.ApiConfig config = properties.apis().get(api);
        if (config == null) {
            throw new IllegalArgumentException("Ukjent API '" + api + "', kjende: " + properties.apis().keySet());
        }
        Map<String, String> remaining = new LinkedHashMap<>(params);
        String path = fillTemplate(config.pathTemplate(), remaining);
        try {
            return client.get(config, path, remaining);
        } catch (RuntimeException e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "FEIL");
            result.put("feilmelding", rootCauseMessage(e));
            return result;
        }
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    private static String fillTemplate(String template, Map<String, String> params) {
        Set<String> used = new java.util.HashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = params.get(name);
            if (value == null) {
                throw new IllegalStateException("Mangler parameter '" + name + "' for stiltemplate " + template);
            }
            used.add(name);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        used.forEach(params::remove);
        return sb.toString();
    }
}
