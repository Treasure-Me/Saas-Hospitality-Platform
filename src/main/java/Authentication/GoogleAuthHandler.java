package Authentication;

import Database.DatabaseEditor;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.SameSite;

import java.util.Collections;
import java.util.Map;

public class GoogleAuthHandler {

    private static final String CLIENT_ID = System.getProperty("GOOGLE_CLIENT_ID", System.getenv("GOOGLE_CLIENT_ID"));

    public static void handleGoogleLogin(Context ctx) {
        try {
            // 1. Get the token sent from the frontend
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String idTokenString = body.get("credential");

            if (idTokenString == null) {
                ctx.status(400).json(Map.of("success", false, "message", "Missing Google credential."));
                return;
            }

            // 2. Verify the token with Google securely
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(CLIENT_ID))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);

            if (idToken != null) {
                Payload payload = idToken.getPayload();

                // 3. Extract the user's data from Google
                String email = payload.getEmail();
                String firname = (String) payload.get("firstname");
                String lastname = (String) payload.get("lastname");

                if (DatabaseEditor.getUserByEmail(email) == null) {
                    String randomPassword = java.util.UUID.randomUUID().toString();
                    DatabaseEditor.addUser(firname, lastname, email, "N/A", randomPassword);
                }

                String token = JwtUtil.generateToken(email);

                Cookie jwtCookie = new Cookie("zen_session", token);
                jwtCookie.setHttpOnly(true);
                jwtCookie.setPath("/");
                jwtCookie.setMaxAge(7 * 24 * 60 * 60);
                jwtCookie.setSameSite(SameSite.NONE);
                jwtCookie.setSecure(true);

                ctx.cookie(jwtCookie);
                ctx.json(Map.of("success", true, "message", "Google Login Successful"));

            } else {
                ctx.status(401).json(Map.of("success", false, "message", "Invalid Google ID token."));
            }
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).json(Map.of("success", false, "message", "Server error during Google auth."));
        }
    }
}