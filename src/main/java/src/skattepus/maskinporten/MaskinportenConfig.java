package src.skattepus.maskinporten;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import no.ks.fiks.maskinporten.Maskinportenklient;
import no.ks.fiks.maskinporten.MaskinportenklientProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import src.skattepus.skattedata.SkattedataProperties;
import org.springframework.boot.ssl.pem.PemContent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;

/**
 * Spring configuration for the Maskinporten setup. Uses the same client and setup
 * pattern as anlegg-common-svarut: KS FIKN's {@code maskinporten-client} with a
 * private PEM key and the KEYID from the Maskinporten portal in the JWS header.
 */
@Configuration
@EnableConfigurationProperties({MaskinportenProperties.class, SkattedataProperties.class})
public class MaskinportenConfig {

    /**
     * Builds the Maskinporten client with the private key, audience, token endpoint
     * and KEYID in the JWS header, and wraps it in an access token provider.
     *
     * @param properties the Maskinporten connection properties
     * @return provider that fetches (and caches) access tokens for the configured scope
     * @throws IOException if the private key file cannot be read
     */
    @Bean
    public MaskinportenAccessTokenProvider accessTokenProvider(MaskinportenProperties properties) throws IOException {
        PrivateKey privateKey = readPrivateKey(Path.of(properties.privateKeyFile()));
        Maskinportenklient maskinportenClient = Maskinportenklient.builder()
                .withPrivateKey(privateKey)
                .withProperties(MaskinportenklientProperties.builder()
                        .numberOfSecondsLeftBeforeExpire(properties.secondsBeforeExpire())
                        .issuer(properties.clientId())
                        .audience(properties.audience())
                        .tokenEndpoint(properties.tokenEndpoint())
                        .build())
                .usingJwsHeaderProvider(() -> new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(properties.keyId())
                        .build())
                .build();
        return new MaskinportenAccessTokenProvider(maskinportenClient, properties.scope());
    }

    private static PrivateKey readPrivateKey(Path path) throws IOException {
        return PemContent.of(Files.readString(path)).getPrivateKey();
    }
}
