package src.skattepus.skattedata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import src.skattepus.maskinporten.MaskinportenAccessTokenProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Simple Bearer token client for testing the Skatteetaten APIs against the test or
 * prod environment. Fetches a token per scope via Maskinporten, and returns the
 * status, raw body and Korrelasjonsid so access and raw data can be verified without
 * domain types.
 */
@Service
public class SkattedataClient {

    private static final Logger LOG = LoggerFactory.getLogger(SkattedataClient.class);

    private final RestClient restClient;
    private final MaskinportenAccessTokenProvider tokenProvider;

    /**
     * Constructs the client with the default RestClient.
     *
     * @param tokenProvider provider supplying Maskinporten tokens per scope
     */
    @Autowired
    public SkattedataClient(MaskinportenAccessTokenProvider tokenProvider) {
        this(tokenProvider, RestClient.builder());
    }

    /**
     * Constructs the client with an explicit builder, for testability and eventual
     * shared configuration (timeouts, proxies).
     *
     * @param tokenProvider     provider supplying Maskinporten tokens per scope
     * @param restClientBuilder the builder to use for the HTTP calls
     */
    public SkattedataClient(MaskinportenAccessTokenProvider tokenProvider, RestClient.Builder restClientBuilder) {
        this.tokenProvider = tokenProvider;
        this.restClient = restClientBuilder.build();
    }

    /**
     * Performs a GET against the API with a Bearer token for the API scope.
     *
     * @param api         the API configuration (scope and base URL)
     * @param path        the resolved path from the path template
     * @param queryParams optional query parameters (may be empty)
     * @return status code, raw body and correlation id
     */
    public Map<String, Object> get(SkattedataProperties.ApiConfig api, String path, Map<String, String> queryParams) {
        String correlationId = generateCorrelationId();
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(api.baseUrl() + path);
        queryParams.forEach(uri::queryParam);

        ResponseEntity<String> response = restClient.get()
                .uri(uri.build().toUri())
                .headers(h -> {
                    h.setBearerAuth(tokenProvider.getAccessToken(api.scope()));
                    h.set("Korrelasjonsid", correlationId);
                    h.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .retrieve()
                .onStatus(status -> true, (req, res) -> {
                })
                .toEntity(String.class);

        LOG.info("GET {} -> {} (korrelasjonsid={})", uri.toUriString(), response.getStatusCode().value(), correlationId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", response.getStatusCode().value());
        result.put("body", response.getBody());
        result.put("korrelasjonsid", correlationId);
        return result;
    }

    private static String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
