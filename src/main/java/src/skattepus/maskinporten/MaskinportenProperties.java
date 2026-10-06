package src.skattepus.maskinporten;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Eigenskapar for oppkobling mot Maskinporten (test eller prod), speglar same
 * oppsett som i anlegg-common-svarut (KS FIKN maskinporten-client).
 *
 * @param clientId             client-id i Maskinporten (= org.nummer for test)
 * @param keyId                KEYID fra Maskinporten-portalen (fingeravtrykk av det offentlege sertifikatet)
 * @param privateKeyFile       filsti til PEM-private-nøkkel som signerer client-assertion
 * @param audience             Maskinporten sitt audience (test: https://test.maskinporten.no/)
 * @param tokenEndpoint        Maskinporten sitt token-endepunkt
 * @param scope                tilgangen/scope som ettersprias, tildelt i Maskinporten-portalen
 * @param secondsBeforeExpire  kor lenge før utløp tokenet skal fornyast automatisk
 */
@ConfigurationProperties(prefix = "skattepus.maskinporten")
public record MaskinportenProperties(
        String clientId,
        String keyId,
        String privateKeyFile,
        @DefaultValue("https://test.maskinporten.no/") String audience,
        @DefaultValue("https://test.maskinporten.no/token") String tokenEndpoint,
        String scope,
        @DefaultValue("10") int secondsBeforeExpire) {
}
