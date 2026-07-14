package Authentication;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.util.Date;

public class JwtUtil {
    // Read the secret from your .env file
    private static final String SECRET = System.getProperty("JWT_SECRET", "fallback_secret_for_local_dev");
    private static final Algorithm algorithm = Algorithm.HMAC256(SECRET);

    // 1. Create a token that lasts for 7 days
    public static String generateToken(String email) {
        return JWT.create()
                .withIssuer("Zen-63")
                .withClaim("email", email)
                .withExpiresAt(new Date(System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000))) // 7 days
                .sign(algorithm);
    }

    // 2. Read the token and make sure it is valid
    public static String verifyAndGetEmail(String token) {
        try {
            DecodedJWT jwt = JWT.require(algorithm)
                    .withIssuer("Zen-63")
                    .build()
                    .verify(token);

            return jwt.getClaim("email").asString();
        } catch (Exception e) {
            return null; // Token is expired, forged, or invalid
        }
    }
}
