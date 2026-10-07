package src.skattepus.maskinporten;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Properties for the Maskinporten connection (test or prod), mirroring the setup used
 * by anlegg-common-svarut (KS FIKN maskinporten-client).
 *
 * @param clientId             client id in Maskinporten
 * @param keyId                KEYID (UUID) from the Maskinporten portal, not the certificate fingerprint
 * @param privateKeyFile       path to the private PEM key that signs the client assertion
 * @param audience             Maskinporten audience (test: https://test.maskinporten.no/)
 * @param tokenEndpoint        Maskinporten token endpoint
 * @param scope                the scope being requested, granted in the Maskinporten portal
 * @param rightsPackages       the rettighetspakke path parameters (comma separated) that every API test must run against
 * @param secondsBeforeExpire  how long before expiry the token is refreshed automatically
 */
@ConfigurationProperties(prefix = "skattepus.maskinporten")
public record MaskinportenProperties(
        String clientId,
        String keyId,
        String privateKeyFile,
        @DefaultValue("https://test.maskinporten.no/") String audience,
        @DefaultValue("https://test.maskinporten.no/token") String tokenEndpoint,
        String scope,
        @DefaultValue("") List<String> rightsPackages,
        @DefaultValue("10") int secondsBeforeExpire) {
}
