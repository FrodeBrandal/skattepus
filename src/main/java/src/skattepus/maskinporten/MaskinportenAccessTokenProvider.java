package src.skattepus.maskinporten;

import no.ks.fiks.maskinporten.Maskinportenklient;

/**
 * Leverandør av Maskinporten access_token. Samde klasse som i anlegg-common-svarut,
 * slik at oppsettet vårt er identisk med dei andre integrasjonane deira.
 */
public class MaskinportenAccessTokenProvider {

    private final Maskinportenklient maskinportenklient;
    private final String scope;

    /**
     * Constructs the provider.
     *
     * @param maskinportenklient the KS FIKN Maskinporten client (handles assertion signing and token caching)
     * @param scope              the access token scope to request
     */
    public MaskinportenAccessTokenProvider(Maskinportenklient maskinportenklient, String scope) {
        this.maskinportenklient = maskinportenklient;
        this.scope = scope;
    }

    /**
     * Returns a valid access token for the configured scope, refreshing it automatically
     * shortly before expiry.
     *
     * @return the Maskinporten access token (JWT)
     */
    public String getAccessToken() {
        return maskinportenklient.getAccessToken(scope);
    }

    /**
     * Returns a valid access token for an explicit scope, refreshing it automatically
     * shortly before expiry. The underlying client caches tokens per scope.
     *
     * @param requestedScope the access token scope to request
     * @return the Maskinporten access token (JWT)
     */
    public String getAccessToken(String requestedScope) {
        return maskinportenklient.getAccessToken(requestedScope);
    }
}
