package src.skattepus.live;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real test against the Mva-fastsetting API
 * ({@code skattepus.skatteetaten.apis.mvafastsetting}) — assessed VAT per
 * organisation for a period. The period (fraOgMed/tilOgMed) is derived from
 * {@code mva.year} and sent as query parameters. One dynamic test per
 * rettighetspakke from {@code keys/secret.yml}.
 *
 * <p>Proof of usability: the response is deserialized into typed records, the VAT
 * amounts are summed and a receipt is printed to stdout. Unknown fields in the
 * API payload are ignored (Jackson 3 default).
 */
class MvaFastsettingTest extends AbstractSkatteetatenTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    record MvaFastsettingResponse(String skattepliktig, List<MvaPeriode> forespurtSkattleggingsperiode) {
    }

    record MvaPeriode(MvaFastsetting egenfastsattMerverdiavgift, MvaFastsetting fastsattMerverdiavgift) {
    }

    record MvaFastsetting(MvaMelding mvaMelding, List<MvaLinje> mvaSpesifikasjonslinje) {
    }

    record MvaMelding(String meldingsreferanse, String innsendingstidspunkt) {
    }

    record MvaLinje(String mvaKode, Double grunnlag, String sats, Double merverdiavgift, Boolean erFradrag) {
    }

    @TestFactory
    Stream<DynamicTest> fetchesVatAssessments() {
        String orgnr = prop("mva.orgnr", "orgnr");
        String year = prop("mva.year");
        return forEveryRightsPackage(pkg -> {
            String body = callApi("mvafastsetting", Map.of(
                    "rettighetspakke", pkg,
                    "organisasjonsnummer", orgnr,
                    "fraOgMed", year + "-01-01",
                    "tilOgMed", year + "-12-31"));

            MvaFastsettingResponse response = JSON.readValue(body, MvaFastsettingResponse.class);

            assertThat(response.skattepliktig()).isEqualTo(orgnr);
            assertThat(response.forespurtSkattleggingsperiode()).as("skattleggingsperiodar").isNotEmpty();

            List<MvaLinje> linjer = response.forespurtSkattleggingsperiode().stream()
                    .map(p -> p.egenfastsattMerverdiavgift() != null
                            ? p.egenfastsattMerverdiavgift() : p.fastsattMerverdiavgift())
                    .filter(Objects::nonNull)
                    .flatMap(f -> f.mvaSpesifikasjonslinje() == null
                            ? Stream.empty() : f.mvaSpesifikasjonslinje().stream())
                    .toList();
            assertThat(linjer).as("mva-spesifikasjonslinjer").isNotEmpty();
            double sumVat = linjer.stream().mapToDouble(l -> l.merverdiavgift() == null ? 0 : l.merverdiavgift()).sum();

            String referanse = response.forespurtSkattleggingsperiode().stream()
                    .map(p -> p.egenfastsattMerverdiavgift() != null
                            ? p.egenfastsattMerverdiavgift() : p.fastsattMerverdiavgift())
                    .filter(Objects::nonNull)
                    .map(MvaFastsetting::mvaMelding)
                    .filter(Objects::nonNull)
                    .map(MvaMelding::meldingsreferanse)
                    .findFirst().orElse("(ingen meldingsreferanse)");

            System.out.printf("[LIVE] MOTTOK: mva %s for %s (%s): %d spesifikasjonslinjer, sum avgift %.2f, meldingsreferanse %s%n",
                    year, orgnr, pkg, linjer.size(), sumVat, referanse);
        });
    }
}
