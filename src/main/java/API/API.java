package API;

import java.util.Map;

import Authentication.GoogleAuthHandler;
import Authentication.JwtUtil;
import Database.util.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javalin.Javalin;

public class API {
    private final Javalin server;
    static final Logger logger = LoggerFactory.getLogger(API.class);

    public API() {
        server = Javalin.create(config -> {
                    config.bundledPlugins.enableCors(cors -> {
                        cors.addRule(it -> {
                            it.allowHost(
                                    "https://zen-63.vercel.app",
                                    "http://localhost:63342",
                                    "http://localhost:7070",
                                    "http://127.0.0.1:7070"
                            );

                            it.allowCredentials = true;
                        });
                    });
                })
                // THE BOUNCER: Protect any route that starts with /api/private/
                .before("/api/private/*", ctx -> {
                    // 1. Look for the cookie
                    String token = ctx.cookie("zen_session");

                    if (token == null) {
                        ctx.status(401).json(Map.of("success", false, "message", "Unauthorized. Please log in."));
                        return;
                    }

                    // 2. Verify the passport is real and not expired
                    String userEmail = JwtUtil.verifyAndGetEmail(token);
                    if (userEmail == null) {
                        // Token is fake or expired. Clear the bad cookie.
                        ctx.removeCookie("zen_session");
                        ctx.status(401).json(Map.of("success", false, "message", "Session expired. Please log in."));
                        return;
                    }

                    // 3. Success! Store the email in the context so the next function knows who is asking!
                    ctx.attribute("userEmail", userEmail);
                })
                .after(ctx -> {
                    logger.info("Request: {} {}", ctx.method(), ctx.url());
                });

        this.server.post("/api/new-booking", APIHandler::newBooking);
        this.server.post("/api/new-order", APIHandler::newOrder);
        this.server.post("api/private/new-booking", APIHandler::newLoggedBooking);
        this.server.post("/api/login", APIHandler::login);
        this.server.post("/api/logout", APIHandler::logout);
        this.server.post("/api/create-account", APIHandler::createAccount);
        this.server.post("/api/auth/google", GoogleAuthHandler::handleGoogleLogin);
        this.server.post("/api/payments/webhook", APIHandler::handlePaymentWebhook);
        this.server.post("api/private/catalog", APIHandler::addCatalog);
        this.server.get("/api/places-search", APIHandler::searchCustomStudio);
        this.server.get("/api/private/dashboard-data", APIHandler::getDashboardData);
        this.server.get("api/private/catalog", APIHandler::getCatalog);
        this.server.get("/api/private/dashboard/stats", APIHandler::getAdminStats);
        this.server.get("/api/private/reservations/pending", APIHandler::getReservations);
        this.server.get("/api/private/dashboard/activity", APIHandler::getRecentAdminActivity);
        this.server.get("/api/private/orders/active", APIHandler::getActiveOrders);
        this.server.put("api/private/catalog/{id}/price", APIHandler::updateCatalogPrice);
        this.server.put("/api/private/reservations/{id}/status", APIHandler::updateOrderStatus);
        this.server.delete("/api/private/bookings", APIHandler::cancelBooking);

    }

    public static void main(String[] args) {
        DatabaseConfig.loadEnvironment();
        API server = new API();

        String portStr = System.getenv("PORT");
        int port = (portStr != null) ? Integer.parseInt(portStr) : 7070;

        System.out.println("Starting server on port: " + port);
        server.start(port);
    }

    public void start(int port) {
        this.server.start(port);
    }

    public void stop() {
        this.server.stop();
    }
}