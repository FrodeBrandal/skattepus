package src.skattepus.maskinporten;

import com.nimbusds.jwt.SignedJWT;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enkleste moglege test av Maskinporten-oppslutning: hentar access_token for dei
 * konfigurerte tilgangane og returnerar token samt dekode JWT-claims slik at ein kan
 * verifisere scope, utsteder, audience og ekspirasjon.
 */
@RestController
@RequestMapping("/maskinporten")
public class MaskinportenTokenController {

    private final MaskinportenAccessTokenProvider accessTokenProvider;

    /**
     * Constructs the controller.
     *
     * @param accessTokenProvider the provider fetching tokens from Maskinporten
     */
    public MaskinportenTokenController(MaskinportenAccessTokenProvider accessTokenProvider) {
        this.accessTokenProvider = accessTokenProvider;
    }

    /**
     * Fetches an access_token and returns it together with the decoded JWT claims
     * (iss, aud, scope, exp and so on).
     *
     * @return token response with access token and decoded claims
     * @throws IllegalArgumentException if the returned token is not a valid signed JWT
     */
    @GetMapping("/token")
    public Map<String, Object> token() {
        String accessToken = accessTokenProvider.getAccessToken();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accessToken", accessToken);
        result.put("claims", decodeClaims(accessToken));
        return result;
    }

    private static Map<String, Object> decodeClaims(String jwt) {
        try {
            return SignedJWT.parse(jwt).getJWTClaimsSet().toJSONObject();
        } catch (ParseException e) {
            throw new IllegalArgumentException("Responsen var ikkje ein gyldig signert JWT", e);
        }
    }
}
