package src.skattepus.live;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Map;
import java.util.stream.Stream;

/**
 * Real test against the Aksjebeholdning API
 * ({@code skattepus.skatteetaten.apis.aksjebeholdning}) — one dynamic test per
 * rettighetspakke from {@code keys/secret.yml}.
 */
class AksjebeholdningTest extends AbstractSkatteetatenTest {

    @TestFactory
    Stream<DynamicTest> fetchesPersonalShareholding() {
        return forEveryRightsPackage(pkg -> callApi("aksjebeholdning", Map.of(
                "rettighetspakke", pkg,
                "kalenderaar", prop("year"),
                "ident", prop("person.ident"))));
    }
}
