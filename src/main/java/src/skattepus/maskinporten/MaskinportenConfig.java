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
 * Spring-konfigurasjon for Maskinporten-oppslutning. Brukar same klient og same
 * oppsettsmønster som anlegg-common-svarut: KS FIKN sin {@code maskinporten-client}
 * med privat nøkel (PEM) og KEYID-frå Maskinporten-portalen i JWS-headeren.
 */
@Configuration
@EnableConfigurationProperties({MaskinportenProperties.class, SkattedataProperties.class})
public class MaskinportenConfig {

    /**
     * Bygger Maskinporten-klienten med privat nøkel, audience, token-endepunkt og
     * KEYID i JWS-headeren, og pakkar han inn i ein access-token-leverandør.
     *
     * @param properties the Maskinporten connection properties
     * @return provider that fetches (and caches) access tokens for the configured scope
     * @throws IOException if the private key file cannot be read
     */
    @Bean
    public MaskinportenAccessTokenProvider accessTokenProvider(MaskinportenProperties properties) throws IOException {
        PrivateKey privateKey = readPrivateKey(Path.of(properties.privateKeyFile()));
        Maskinportenklient maskinportenklient = Maskinportenklient.builder()
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
        return new MaskinportenAccessTokenProvider(maskinportenklient, properties.scope());
    }

    private static PrivateKey readPrivateKey(Path path) throws IOException {
        return PemContent.of(Files.readString(path)).getPrivateKey();
    }
}
