package src.skattepus.live;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real test against the Næringsspesifikasjon API
 * ({@code skattepus.skatteetaten.apis.naeringsspesifikasjon}) — one dynamic test
 * per rettighetspakke from {@code keys/secret.yml}.
 *
 * <p>The two packages expose <b>different</b> information (confirmed by the
 * requirement spec and live responses): frivillighetsstøtte gets
 * {@code sumDriftskostnad}, stiftelsestilsyn gets {@code virksomhetstype}. A
 * 200 response also implicitly confirms that the business specifics have been
 * delivered — if not delivered, the API returns 404 and there is nothing to share.
 */
class NaeringsspesifikasjonTest extends AbstractSkatteetatenTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TestFactory
    Stream<DynamicTest> fetchesBusinessSpecifics() {
        return forEveryRightsPackage(pkg -> {
            String body = callApi("naeringsspesifikasjon", Map.of(
                    "rettighetspakke", pkg,
                    "inntektsaar", prop("naering.year", "year"),
                    "ident", prop("naering.ident", "orgnr")));
            JsonNode json = JSON.readTree(body);
            if (pkg.toLowerCase().contains("frivillighetsstoette")) {
                JsonNode sum = json.at("/resultatregnskap/driftskostnad/sumDriftskostnad");
                assertThat(sum.isNumber())
                        .as("frivillighetsstøtte-pakken skal gi sum driftskostnad")
                        .isTrue();
                System.out.printf("[LIVE] MOTTOK: næring %s/%s (%s) — sum driftskostnad %.2f%n",
                        prop("naering.ident", "orgnr"), prop("naering.year", "year"), pkg, sum.asDouble());
            } else {
                String type = json.at("/virksomhet/virksomhetstype").asText();
                assertThat(type)
                        .as("stiftelsestilsyn-pakken skal gi virksomhetstype")
                        .isNotBlank();
                System.out.printf("[LIVE] MOTTOK: næring %s/%s (%s) — virksomhetstype %s%n",
                        prop("naering.ident", "orgnr"), prop("naering.year", "year"), pkg, type);
            }
        });
    }
}
