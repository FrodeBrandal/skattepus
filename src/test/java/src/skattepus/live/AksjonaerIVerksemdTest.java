package src.skattepus.live;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real test against the Aksjonær i verksemd API
 * ({@code skattepus.skatteetaten.apis.aksjonaer}) — one dynamic test per
 * rettighetspakke from {@code keys/secret.yml}.
 *
 * <p>Proof of usability: the response is deserialized into typed records and a
 * human-readable receipt of the received shareholders is printed to stdout.
 */
class AksjonaerIVerksemdTest extends AbstractSkatteetatenTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    record AksjonaerResponse(List<Aksjonaer> aksjonaerer) {
    }

    record Aksjonaer(String navn, String foedselsaar, String landkode, List<Aksje> aksjer) {
    }

    record Aksje(String isinnummer, Integer antallAksjer, String aksjeklasse) {
    }

    @TestFactory
    Stream<DynamicTest> fetchesShareholdersOfCompany() {
        String orgnr = prop("orgnr");
        return forEveryRightsPackage(pkg -> {
            String body = callApi("aksjonaer", Map.of(
                    "rettighetspakke", pkg,
                    "kalenderaar", prop("year"),
                    "organisasjonsnummer", orgnr));

            AksjonaerResponse response = JSON.readValue(body, AksjonaerResponse.class);

            assertThat(response.aksjonaerer()).as("aksjonærliste for " + orgnr).isNotEmpty();
            Aksjonaer first = response.aksjonaerer().get(0);
            assertThat(first.navn()).isNotBlank();
            assertThat(first.aksjer()).isNotEmpty();
            assertThat(first.aksjer().get(0).antallAksjer()).isPositive();

            System.out.printf("[LIVE] MOTTOK: %d aksjonærer i %s (%s) — første: %s (%s, %d aksjer %s)%n",
                    response.aksjonaerer().size(), orgnr, pkg, first.navn(), first.foedselsaar(),
                    first.aksjer().get(0).antallAksjer(), first.aksjer().get(0).aksjeklasse());
        });
    }
}
