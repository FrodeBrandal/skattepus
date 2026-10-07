package src.skattepus.live;

import org.junit.jupiter.api.Test;
import src.skattepus.maskinporten.MaskinportenAccessTokenProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real test of the Maskinporten flow: real assertion against test.maskinporten.no
 * and real JWT responses. Covers {@link MaskinportenAccessTokenProvider} and the
 * setup in {@code keys/secret.yml} — client id, KEYID and private key.
 */
class MaskinportenTokenTest extends AbstractSkatteetatenTest {

    @Test
    void fetchesRealTokenForConfiguredScope() throws Exception {
        assertCredentialsPresent();

        String scope = maskinportenProperties.scope();
        String token = fetchTokenOrExplain(scope);

        assertThat(token).as("access_token for scope '" + scope + "'").isNotBlank();
        assertThat((String) decodedClaims(token).get("scope")).contains(scope);
    }

    @Test
    void probesTokensForAllConfiguredScopes() {
        assertCredentialsPresent();

        Map<String, String> results = new LinkedHashMap<>();
        List<String> rejected = new ArrayList<>();
        skattedataProperties.apis().forEach((name, api) -> {
            try {
                String token = tokenProvider.getAccessToken(api.scope());
                results.put(name, "OK (" + token.length() + " chars)");
            } catch (Exception e) {
                String reason = rootCauseMessage(e);
                results.put(name, "REJECTED: " + reason);
                rejected.add("\n  - " + name + " (" + api.scope() + "): " + explainTokenError(reason));
            }
        });
        results.forEach((name, answer) -> System.out.println("[LIVE] scope probe " + name + ": " + answer));

        assertThat(rejected)
                .as("scope Maskinporten avviste for client-id " + maskinportenProperties.clientId()
                        + " — desse må tildelast klienten (test-miljø) av Skatteetaten/Samarbeidsportalen:")
                .isEmpty();
    }
}
