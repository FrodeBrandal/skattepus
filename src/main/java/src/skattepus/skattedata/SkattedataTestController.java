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
 * Test controller for verifying access to the Skatteetaten APIs via Maskinporten.
 * Each API's path template ({@code skattepus.skatteetaten.apis.*}) is filled in with
 * the parameters you send; parameters not part of the path are sent as query parameters.
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
     * @param properties the configured Skatteetaten APIs
     * @param client     Bearer token client performing the actual calls
     */
    public SkattedataTestController(SkattedataProperties properties, SkattedataClient client) {
        this.properties = properties;
        this.client = client;
    }

    /**
     * Lists the configured APIs with scope, base URL and path template.
     *
     * @return map from API name to configuration
     */
    @GetMapping("/apis")
    public Map<String, SkattedataProperties.ApiConfig> apis() {
        return properties.apis();
    }

    /**
     * Performs a test call against the selected API. Parameters matching a
     * {placeholder} in the path template are inserted into the path (without URL
     * encoding); the rest are sent as query parameters.
     *
     * @param api    name of the API, see {@code /skattedata/apis}
     * @param params parameters for the path template and any query parameters
     * @return status code, raw response body and Korrelasjonsid; on failure (e.g. a
     *         rejected Maskinporten token) status ERROR with the root cause
     * @throws IllegalArgumentException if the API is not configured
     * @throws IllegalStateException    if required placeholders are missing
     */
    @GetMapping("/{api}")
    public Map<String, Object> call(@PathVariable String api, @RequestParam Map<String, String> params) {
        SkattedataProperties.ApiConfig config = properties.apis().get(api);
        if (config == null) {
            throw new IllegalArgumentException("Unknown API '" + api + "', known: " + properties.apis().keySet());
        }
        Map<String, String> remaining = new LinkedHashMap<>(params);
        String path = fillTemplate(config.pathTemplate(), remaining);
        try {
            return client.get(config, path, remaining);
        } catch (RuntimeException e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "ERROR");
            result.put("errorMessage", rootCauseMessage(e));
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
                throw new IllegalStateException("Missing parameter '" + name + "' for path template " + template);
            }
            used.add(name);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        used.forEach(params::remove);
        return sb.toString();
    }
}
