package src.skattepus.skattedata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import src.skattepus.maskinporten.MaskinportenAccessTokenProvider;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enkel Bearer-token-klient for å teste Skatteetaten sine DELING-api mot test- eller
 * prod-miljø. Hentar token per scope via Maskinporten, og returnerer status + ru body
 * (og Korrelasjonsid) slik at ein kan verifisere tilgang og rådata utan domenetypar.
 */
@Service
public class SkattedataClient {

    private static final Logger LOG = LoggerFactory.getLogger(SkattedataClient.class);
    private static final DateTimeFormatter KORRELASJONSID_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSSX", Locale.ENGLISH);

    private final RestClient restClient;
    private final MaskinportenAccessTokenProvider tokenProvider;

    /**
     * Constructs the client.
     *
     * @param tokenProvider provider som levererar Maskinporten-token per scope
     */
    public SkattedataClient(MaskinportenAccessTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
        this.restClient = RestClient.create();
    }

    /**
     * Utfører ein GET mot API-et med Bearer-token for API-et sin scope.
     *
     * @param api          API-konfigurasjonen (scope, base-URL, ferdig path)
     * @param queryParams  eventuelle query-parameter (kan vere tom)
     * @return statuskode, rå body og Korrelasjonsid frå Svarte-Mila
     */
    public Map<String, Object> get(SkattedataProperties.ApiConfig api, String path, Map<String, String> queryParams) {
        String korrelasjonsid = generateKorrelasjonsid();
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(api.baseUrl() + path);
        queryParams.forEach(uri::queryParam);

        ResponseEntity<String> response = restClient.get()
                .uri(uri.build().toUri())
                .headers(h -> {
                    h.setBearerAuth(tokenProvider.getAccessToken(api.scope()));
                    h.set("Korrelasjonsid", korrelasjonsid);
                    h.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .retrieve()
                .onStatus(status -> true, (req, res) -> {
                })
                .toEntity(String.class);

        LOG.info("GET {} -> {} (korrelasjonsid={})", uri.toUriString(), response.getStatusCode().value(), korrelasjonsid);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", response.getStatusCode().value());
        result.put("body", response.getBody());
        result.put("korrelasjonsid", korrelasjonsid);
        return result;
    }

    private static String generateKorrelasjonsid() {
        return OffsetDateTime.now(ZoneOffset.UTC).format(KORRELASJONSID_FMT) + "-"
                + Integer.toString(ThreadLocalRandom.current().nextInt(0x10000, 0x100000), 16)
                + "-" + UUID.randomUUID();
    }
}
