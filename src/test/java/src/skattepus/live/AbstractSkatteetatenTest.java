package src.skattepus.live;

import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DynamicTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import src.skattepus.maskinporten.MaskinportenAccessTokenProvider;
import src.skattepus.maskinporten.MaskinportenProperties;
import src.skattepus.skattedata.SkattedataClient;
import src.skattepus.skattedata.SkattedataProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base for real integration tests against the Skatteetaten APIs — <b>no mocks</b>:
 * real private key, real client id/KEYID from {@code keys/secret.yml} and real HTTP
 * against {@code *.skatteetaten-test.no}.
 *
 * <p>Each test class covers one API and inherits {@link #callApi(String, Map)}, which
 * requires HTTP 200 and otherwise fails with a message explaining the cause and what
 * to do about it. No tests are skipped.
 *
 * <p>Test data (fnr/orgnr/year) comes from
 * {@code src/main/resources/keys/live-testdata.properties} — a git-ignored file next
 * to {@code secret.yml}, so no environment variables. The rettighetspakke list comes
 * from {@code secret.yml} itself and is exercised via {@link #forEveryRightsPackage}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
abstract class AbstractSkatteetatenTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]+)}");

    protected static final Path TESTDATA_FILE =
            Path.of("src", "main", "resources", "keys", "live-testdata.properties");

    /** Each test data key has its own explanation of where to find the value. */
    private static final Map<String, String> SOURCE_HELP = Map.of(
            "year", "kalenderår/inntektsår som har testdata for identiteten — merk årsgatelynda: "
                    + "eldre år i dokumenta slepp ikkje lenger gjennom (2025 verkar per okt 2026)",
            "person.ident", "11-sifra fnr med testdata — sjå Test-fanen på "
                    + "skatteetaten.github.io/api-dokumentasjon/api/aksjebeholdning",
            "orgnr", "9-sifra orgnr med testdata — sjå Test-fanen på "
                    + "skatteetaten.github.io/api-dokumentasjon/api/aksjonaerivirksomhet",
            "naering.ident", "ident med næringsspesifikasjon — sjå responsdøme i "
                    + "naeringsspesifikasjon-dokumentasjonen (14895398862 er enkeltmannsføretak)",
            "mva.orgnr", "orgnr med mva-fastsetting — Test-fanen på "
                    + "skatteetaten.github.io/api-dokumentasjon/api/mvafastsetting listar 12 orgnr",
            "mva.year", "år med mva-fastsetting for orgnr-et — perioden blir året sin 01-01 til 12-31");

    private static final Properties TESTDATA = loadTestdata();

    @Autowired
    protected MaskinportenAccessTokenProvider tokenProvider;

    @Autowired
    protected MaskinportenProperties maskinportenProperties;

    @Autowired
    protected SkattedataProperties skattedataProperties;

    @Autowired
    protected SkattedataClient client;

    /**
     * Performs one real GET call against the API, prints status and raw body to
     * stdout, and requires HTTP 200. On any other status the test fails with an
     * explanation of the API's error code.
     *
     * @param apiName key in {@code skattepus.skatteetaten.apis}
     * @param params  values for the placeholders in the path template, the rest become query parameters
     * @return the raw response body, for callers that need to assert on content
     */
    protected String callApi(String apiName, Map<String, String> params) {
        assertCredentialsPresent();
        SkattedataProperties.ApiConfig api = skattedataProperties.apis().get(apiName);
        assertThat(api).as("API '" + apiName + "' må vere konfigurert").isNotNull();

        Map<String, String> query = new LinkedHashMap<>(params);
        String path = fillPath(api.pathTemplate(), query);

        Map<String, Object> result = client.get(api, path, query);

        String body = String.valueOf(result.get("body"));
        System.out.println("[LIVE] " + apiName + " " + api.baseUrl() + path + " -> "
                + result.get("status") + " (korrelasjonsid=" + result.get("korrelasjonsid") + ")");
        System.out.println("[LIVE]   body: " + body.substring(0, Math.min(800, body.length())));

        int status = (int) result.get("status");
        assertThat(status)
                .as(() -> "HTTP-status " + status + " for " + apiName + " (Kall: " + api.baseUrl() + path
                        + ", korrelasjonsid=" + result.get("korrelasjonsid") + ")"
                        + "\n  Rått svar: " + body
                        + "\n  " + explainApiResponse(status, body))
                .isEqualTo(200);
        return body;
    }

    /**
     * Fetches a real token for the scope, turning Maskinporten's raw error code into
     * an explained failure message.
     */
    protected String fetchTokenOrExplain(String scope) {
        try {
            return tokenProvider.getAccessToken(scope);
        } catch (Exception e) {
            throw new AssertionError("Maskinporten gav ikkje token for scope '" + scope + "'. "
                    + explainTokenError(rootCause(e).getMessage())
                    + "\nRått svar: " + rootCause(e).getMessage(), e);
        }
    }

    /** Decodes the token as JWT, prints the central claims to stdout and returns them. */
    protected Map<String, Object> decodedClaims(String token) throws Exception {
        Map<String, Object> claims = SignedJWT.parse(token).getJWTClaimsSet().toJSONObject();
        System.out.println("[LIVE] Maskinporten OK — scope=" + claims.get("scope")
                + " iss=" + claims.get("iss") + " exp=" + claims.get("exp"));
        return claims;
    }

    /** A call against one API for one given rettighetspakke; may throw checked exceptions. */
    @FunctionalInterface
    protected interface RightsPackageCall {
        void accept(String rightsPackage) throws Exception;
    }

    /**
     * One dynamic test per rettighetspakke from {@code skattepus.maskinporten.rights-packages}
     * in {@code keys/secret.yml} — every API must be exercised with all the packages.
     *
     * @param call receives the package name and performs the actual {@link #callApi} call
     */
    protected Stream<DynamicTest> forEveryRightsPackage(RightsPackageCall call) {
        List<String> packages = maskinportenProperties.rightsPackages();
        assertThat(packages)
                .as(() -> "rights-packages er ikkje sett i skattepus.maskinporten i " + SECRET_FILE_HELP_TEXT)
                .isNotEmpty();
        return packages.stream()
                .map(pkg -> DynamicTest.dynamicTest("rettighetspakke=" + pkg, () -> call.accept(pkg)));
    }

    /**
     * Reads a mandatory test data value from live-testdata.properties. If the value
     * is missing, the test <b>fails</b> with instructions on what to fill in and
     * where to find it.
     */
    protected static String prop(String key) {
        String value = TESTDATA.getProperty(key);
        assertThat(value)
                .as(() -> "testdata '" + key + "' er ikkje fylt ut i " + TESTDATA_FILE
                        + "\n  Kvar finn ein den: " + SOURCE_HELP.getOrDefault(key, "Test-fanen i API-dokumentasjonen"))
                .isNotBlank();
        return value.trim();
    }

    /**
     * Reads the primary key, falling back to fallbackKey when the primary key is
     * missing or blank. Dataset-specific keys (e.g. {@code naering.ident}) thus
     * override the shared defaults.
     */
    protected static String prop(String primaryKey, String fallbackKey) {
        String value = TESTDATA.getProperty(primaryKey);
        return (value == null || value.isBlank()) ? prop(fallbackKey) : value.trim();
    }

    protected void assertCredentialsPresent() {
        String clientId = maskinportenProperties.clientId();
        assertThat(clientId)
                .as(() -> "client-id er ikkje på plass (" + clientId + "). "
                        + "Skuld: keys/secret.yml manglar eller har ikkje "
                        + "skattepus.maskinporten.client-id. Løysing: sjá 'Oppsett lokalt' i README")
                .isNotBlank()
                .isNotEqualTo("test-client-id");
    }

    /** Turns Maskinporten's error and MP codes into instructions on what to actually do. */
    protected static String explainTokenError(String message) {
        String m = String.valueOf(message);
        if (m.contains("invalid_scope") || m.contains("MP-250") || m.contains("has not been granted access")) {
            return "Skuld: scope-et er ikkje tildelt denne klienten (MP-250). "
                    + "Løysing: be Skatteetaten/Samarbeidsportalen om å tildele scope-et til "
                    + "client-id'en i keys/secret.yml i TEST-miljøet.";
        }
        if (m.contains("invalid_grant") || m.contains("MP-100") || m.contains("Invalid assertion")) {
            return "Skuld: assertion blei avvist (MP-100). "
                    + "Løysing: kontroller client-id, at keys/secret.yml har rett KEYID "
                    + "(UUID frå portalen, ikkje fingeravtrykket) og at privatnøkkelen i "
                    + "private-key-file høver med det offentlege sertifikatet som er registrert.";
        }
        if (m.contains("unknown key identifier") || m.contains("kid")) {
            return "Skuld: KEYID (kid) er ukjend for klienten. "
                    + "Løysing: bruk KEYID-UUID frå Maskinporten-portalen i skattepus.maskinporten.key-id.";
        }
        if (m.contains("MP-101")) {
            return "Skuld: ugyldig scope-format (MP-101). "
                    + "Løysing: scope skal vere utan miljø-suffiks, t.d. skatteetaten:aksjebeholdning.";
        }
        return "Skuld: ukjend tokenfeil — sjá MP-feilkoden i lenkja error_uri i rått svar nedanfor.";
    }

    /** Turns the API's error codes (ABE-/AIV-/NAV-/MVA series) into what needs fixing. */
    protected static String explainApiResponse(int status, String body) {
        String b = String.valueOf(body);
        if (status == 400 && b.contains("rettighetspakke er ugyldig")) {
            return "Skuld: oppgitt rettighetspakke i stien finst ikkje i test-miljøet (token og scope er faktisk i orden). "
                    + "Løysing: rett lista skattepus.maskinporten.rights-packages i "
                    + SECRET_FILE_HELP_TEXT + ".";
        }
        if (status == 400 && (b.contains("kalenderaar er ugyldig") || b.contains("inntektsaar er ugyldig"))) {
            return "Skuld: året blir avvist av årsgatelynda i test-miljøet — datasett med eldre år "
                    + "enn dei dokumenta listar blir serverte likevel ikkje (Test-fanen kan vere utdatert). "
                    + "Løysing: prøv nyare år (year/<api>.year i " + TESTDATA_FILE + ") eller "
                    + "bruk ein annan ident som har data for året.";
        }
        if (status == 400) {
            return "Skuld: ugyldig førespurnad (400). Sjå kode/melding i rått svar — "
                    + "vanlege årsaker er gal dato/år, for lang ident eller manglande query-parameter.";
        }
        if (status == 401) {
            return "Skuld: token blei ikkje akseptert (401). "
                    + "Løysing: kontrollér at scope-et dekkjer nettopp dette API-et og at audience er rett miljø.";
        }
        if (status == 403) {
            return "Skuld: manglande tilgang (403) — truleg manglande delegasjon i Altinn "
                    + "eller at rettighetspakken ikkje gjev rettsleg grunnlag for dette datasettet.";
        }
        if (status == 404) {
            return "Skuld: fanst ikkje data (404). "
                    + "Løysing: bruk ein annan fnr/orgnr eller eit anna år som faktisk har data i test-miljøet "
                    + "(Test-fanen i API-dokumentasjonen listar gyldig testdata per API).";
        }
        if (status >= 500) {
            return "Skuld: feil hos Skatteetaten/infrastrukturen (" + status + "). "
                    + "Løysing: bruk korrelasjonsid'en i svaret når de melder sak — ikkje noko å feilsøke hjå oss.";
        }
        return "Skuld: uventa status " + status + " — sjå rått svar.";
    }

    private static final String SECRET_FILE_HELP_TEXT =
            "keys/secret.yml (verdien er ein kommaseparert list)";

    private static String fillPath(String template, Map<String, String> params) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder path = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = params.remove(name);
            assertThat(value)
                    .as(() -> "callApi manglar parameter '" + name + "' for stimal '" + template + "'")
                    .isNotNull();
            matcher.appendReplacement(path, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(path);
        return path.toString();
    }

    private static Properties loadTestdata() {
        Properties properties = new Properties();
        try {
            if (Files.exists(TESTDATA_FILE)) {
                try (var in = Files.newInputStream(TESTDATA_FILE)) {
                    properties.load(in);
                }
            }
        } catch (IOException e) {
            System.out.println("[LIVE] kunne ikkje lese " + TESTDATA_FILE + ": " + e.getMessage());
        }
        return properties;
    }

    /** Root cause as text — Maskinporten/the client wraps exceptions in several layers. */
    protected static String rootCauseMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    private static Throwable rootCause(Throwable t) {
        return t.getCause() != null && t.getCause() != t ? rootCause(t.getCause()) : t;
    }
}
