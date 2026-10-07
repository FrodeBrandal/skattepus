package src.skattepus.maskinporten;

import com.nimbusds.jwt.SignedJWT;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simplest possible test of the Maskinporten connection: fetches an access token for
 * the configured scope and returns the token together with the decoded JWT claims, so
 * the caller can verify scope, issuer, audience and expiry.
 */
@RestController
@RequestMapping("/maskinporten")
public class MaskinportenTokenController {

    private final MaskinportenAccessTokenProvider accessTokenProvider;

    /**
     * Constructs the controller.
     *
     * @param accessTokenProvider provider fetching tokens from Maskinporten
     */
    public MaskinportenTokenController(MaskinportenAccessTokenProvider accessTokenProvider) {
        this.accessTokenProvider = accessTokenProvider;
    }

    /**
     * Fetches an access token and returns it together with the decoded JWT claims
     * (iss, aud, scope, exp and so on).
     *
     * @return token response with the access token and the decoded claims
     * @throws IllegalArgumentException if the response is not a valid signed JWT
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
            throw new IllegalArgumentException("Response was not a valid signed JWT", e);
        }
    }
}
