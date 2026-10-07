package src.skattepus.maskinporten;

import no.ks.fiks.maskinporten.Maskinportenklient;

/**
 * Supplier of Maskinporten access tokens. Same class as in anlegg-common-svarut, so
 * this setup is identical to our other integrations.
 */
public class MaskinportenAccessTokenProvider {

    private final Maskinportenklient maskinportenClient;
    private final String scope;

    /**
     * Constructs the provider.
     *
     * @param maskinportenClient the KS FIKN Maskinporten client (handles assertion signing and token caching)
     * @param scope              the default scope to request
     */
    public MaskinportenAccessTokenProvider(Maskinportenklient maskinportenClient, String scope) {
        this.maskinportenClient = maskinportenClient;
        this.scope = scope;
    }

    /**
     * Returns a valid access token for the configured scope, refreshed automatically
     * shortly before expiry.
     *
     * @return the Maskinporten access token (JWT)
     */
    public String getAccessToken() {
        return maskinportenClient.getAccessToken(scope);
    }

    /**
     * Returns a valid access token for an explicit scope, refreshed automatically
     * shortly before expiry. The underlying client caches tokens per scope.
     *
     * @param requestedScope the scope to request
     * @return the Maskinporten access token (JWT)
     */
    public String getAccessToken(String requestedScope) {
        return maskinportenClient.getAccessToken(requestedScope);
    }
}
