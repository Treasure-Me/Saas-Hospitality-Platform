package API;

import Authentication.JwtUtil;
import Database.DatabaseEditor;
import Database.util.DataWrapper.*;
import Database.util.DatabaseConfig;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.SameSite;
import io.javalin.http.util.RateLimiter;
import org.jetbrains.annotations.NotNull;
import org.mindrot.jbcrypt.BCrypt;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class APIHandler {
    private static final RateLimiter searchLimiter = new RateLimiter(TimeUnit.MINUTES);

    public static void newOrder(Context ctx) {
        try {

            Payload payload = ctx.bodyAsClass(Payload.class);

            LocalDateTime startDt = LocalDateTime.parse(payload.datetime);
            Timestamp bookingStart = Timestamp.valueOf(startDt);
            HashMap<String, Integer> idQuantityMap = payload.IdMap;

            String tenantId = ctx.queryParam("tenantId");
            String orderId = DatabaseEditor.addOrder(tenantId, bookingStart, payload.price);

            for (String catalogItemId: idQuantityMap.keySet()){
                DatabaseEditor.addOrderItems(orderId, catalogItemId, idQuantityMap.get(catalogItemId), payload.price);
            }

            if ("payfast".equals(payload.type)) {
                String checkoutUrl = Payments.PayFastService.generateCheckoutUrl(orderId, payload.price, payload.email, payload.details);

                ctx.status(200).json(Map.of(
                        "success", true,
                        "redirectUrl", checkoutUrl
                ));

            } else {
                ctx.status(200).json(Map.of(
                        "success", true
                ));
            }

        } catch (SQLException e) {
            if ("P0001".equals(e.getSQLState())) {
                ctx.status(409).json(Map.of(
                        "success", false,
                        "message", e.getMessage()
                ));
            } else {
                e.printStackTrace();
                ctx.status(500).json(Map.of("success", false, "message", "A database error occurred."));
            }
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(400).json(Map.of("success", false, "message", "Invalid order data received."));
        }
    }

    public static void newBooking(Context ctx) {
        try {

            Payload payload = ctx.bodyAsClass(Payload.class);

            LocalDateTime startDt = LocalDateTime.parse(payload.datetime);
            Timestamp bookingStart = Timestamp.valueOf(startDt);

            String tenantId = ctx.queryParam("tenantId");
            String bookingId = DatabaseEditor.addIntoReservations(tenantId, payload.name, payload.email, payload.type, bookingStart, payload.venue);

            if ("payfast".equals(payload.type)) {
                double price = payload.price;
                String checkoutUrl = Payments.PayFastService.generateCheckoutUrl(bookingId, price, payload.email, payload.details);

                ctx.status(200).json(Map.of(
                        "success", true,
                        "redirectUrl", checkoutUrl
                ));

            } else {
                ctx.status(200).json(Map.of(
                        "success", true
                ));
            }

        } catch (SQLException e) {
            if ("P0001".equals(e.getSQLState())) {
                ctx.status(409).json(Map.of(
                        "success", false,
                        "message", e.getMessage()
                ));
            } else {
                e.printStackTrace();
                ctx.status(500).json(Map.of("success", false, "message", "A database error occurred."));
            }
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(400).json(Map.of("success", false, "message", "Invalid booking data received."));
        }
    }

    private static double calculateServicePrice(String id) throws SQLException {
        return DatabaseEditor.getCatalogItemPrice(id);
    }

    public static boolean UserIsCorrect(String email, String password) throws SQLException {
        // Conformed to the backend security architecture (Pull Hash -> Check in Java)
        String sql = "SELECT password_hash FROM users WHERE email = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString(1);

                    if (BCrypt.checkpw(password, storedHash)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void logout(@NotNull Context context) {
        // Implemented secure cookie clearing
        Cookie cookie = new Cookie("zen_session", "");
        cookie.setMaxAge(0);
        cookie.setPath("/");
        context.cookie(cookie);
        context.status(200).json(Map.of("success", true, "message", "Logged out successfully"));
    }

    public static void createAccount(@NotNull Context context) {
        RegistrationRequest registrationRequest = context.bodyAsClass(RegistrationRequest.class);

        String username = registrationRequest.username;
        String email = registrationRequest.email;
        String number = registrationRequest.number;
        String password = registrationRequest.password;

        String generatedSalt = BCrypt.gensalt(12);
        String hashedPassword = BCrypt.hashpw(password, generatedSalt);

        try {
            // Conformed to DatabaseEditor's 5 parameters (added placeholder for lastName)
            DatabaseEditor.addUser(username, "User", email, number, hashedPassword);
            context.status(201).json(Map.of("success", true, "message", "Account created"));
        } catch (SQLException e) {
            if ("P0001".equals(e.getSQLState())) {
                if (e.getMessage().contains("email")) {
                    context.status(409).json(Map.of("success", false, "message", "An account with this email already exists."));
                } else if (e.getMessage().contains("username")) {
                    context.status(409).json(Map.of("success", false, "message", "This username is already taken."));
                } else {
                    context.status(409).json(Map.of("success", false, "message", "User already exists."));
                }
            } else {
                e.printStackTrace();
                context.status(500).json(Map.of("success", false, "message", "A database error occurred."));
            }
        } catch (Exception e) {
            e.printStackTrace();
            context.status(400).json(Map.of("success", false, "message", "Invalid user data received."));
        }
    }

    public static void cancelBooking(@NotNull Context ctx) {
        String email = ctx.attribute("userEmail");
        String bookingId = ctx.queryParam("id");

        try {
            boolean success = DatabaseEditor.cancelReservation(bookingId, email);
            if (success) {
                ctx.json(Map.of("success", true, "message", "Booking cancelled."));
            } else {
                ctx.status(403).json(Map.of("success", false, "message", "Cannot cancel this booking."));
            }
        } catch (SQLException e) {
            ctx.status(500).json(Map.of("success", false, "message", "Database error."));
        }
    }

    public static String getUserLoggedInFromEmail(String email) {
        try {
            User user =  DatabaseEditor.getUserByEmail(email);
            return user != null ? user.username : null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void searchCustomStudio(Context ctx) {
        searchLimiter.incrementCounter(ctx, 10);
        String userInput = ctx.queryParam("location");

        if (userInput == null || userInput.isBlank()) {
            ctx.status(400).json("{\"error\": \"Location query is required\"}");
            return;
        }

        String apiKey = System.getProperty("GOOGLE_MAPS_API_KEY");

        try {
            String encodedQuery = URLEncoder.encode(userInput, StandardCharsets.UTF_8);
            String googleUrl = "https://maps.googleapis.com/maps/api/place/textsearch/json?query=" + encodedQuery + "&key=" + apiKey;

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(googleUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            ctx.contentType("application/json");
            ctx.status(response.statusCode()).result(response.body());

        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(500).json("{\"error\": \"Failed to contact mapping service\"}");
        }
    }

    public static void login(@NotNull Context ctx) {
        try {
            LoginRequest req = ctx.bodyAsClass(LoginRequest.class);

            if (UserIsCorrect(req.email, req.password)) {

                String token = JwtUtil.generateToken(req.email);

                Cookie jwtCookie = new Cookie("zen_session", token);
                jwtCookie.setHttpOnly(true);
                jwtCookie.setSecure(true);
                jwtCookie.setSameSite(SameSite.NONE);
                jwtCookie.setPath("/");
                jwtCookie.setMaxAge(7 * 24 * 60 * 60);

                ctx.cookie(jwtCookie);

                ctx.status(200).json(Map.of("success", true, "message", "Successful login"));
            } else {
                ctx.status(401).json(Map.of("success", false, "message", "Invalid email or password."));
            }

        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(400).json(Map.of("success", false, "message", "Invalid request format."));
        }
    }

    public static List<Booking> getUserBookings(String email) {
        try {
            return DatabaseEditor.getUserReservations(email);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void getDashboardData(@NotNull Context context) {
        String email = context.attribute("userEmail");

        String username = getUserLoggedInFromEmail(email);
        List<Booking> userBookings = getUserBookings(email);

        if (userBookings == null) {
            userBookings = new ArrayList<>();
        }
        if (username == null) {
            username = "Valued Client";
        }

        context.json(Map.of(
                "success", true,
                "user", Map.of("name", username, "email", email),
                "bookings", userBookings
        ));
    }

    public static void newLoggedBooking(@NotNull Context context) {
        String userEmail = context.attribute("userEmail");
        LoggedBookingRequest req = context.bodyAsClass(LoggedBookingRequest.class);
        String tenantId = context.queryParam("tenantId");

        LocalDateTime startDt = LocalDateTime.parse(req.startDate);
        Timestamp bookingStart = Timestamp.valueOf(startDt);

        try {
            User user = DatabaseEditor.getUserByEmail(userEmail);
            DatabaseEditor.addIntoReservations(tenantId, user.username, userEmail, req.service, bookingStart, req.venue);
            context.status(200).json(Map.of("success", true));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void handlePaymentWebhook(Context ctx) {
        String bookingIdStr = ctx.formParam("m_payment_id");
        String paymentStatus = ctx.formParam("payment_status");

        if ("COMPLETE".equalsIgnoreCase(paymentStatus) && bookingIdStr != null) {
            try {
                DatabaseEditor.confirmReservationPayment(bookingIdStr);
                ctx.status(200).result("OK");
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("Database Error");
            }
        } else {
            ctx.status(400).result("Invalid payment notice");
        }
    }

    public static void getCatalog(@NotNull Context context) {
        String id = context.queryParam("tenantId");

        try {
            ArrayList<CatalogItem> catalogItems = DatabaseEditor.getTenantCatalog(id);
            context.status(200).json(catalogItems);

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void addCatalog(@NotNull Context context) {
        CatalogItem catalogItem = context.bodyAsClass(CatalogItem.class);
        String tenantId = context.queryParam("tenantId");

        try {
            DatabaseEditor.addCatalogItem(tenantId, catalogItem);
            context.status(201).json(Map.of("success", true));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void updateCatalogPrice(@NotNull Context context) {
        String catalogItemId = context.pathParam("id");
        String newPrice = context.queryParam("price");

        try {
            DatabaseEditor.updateCatalogItemPrice(catalogItemId, newPrice);
            context.status(200).json(Map.of("success", true));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void getAdminStats(@NotNull Context context) {
        String tenantId = context.queryParam("tenantId");


        try {
            int pendingBookings = DatabaseEditor.getTenantNumberOfPendingReservations(tenantId);
            int carsInBay = DatabaseEditor.getCarsInBay(tenantId);
            double revenue = DatabaseEditor.getTotalOrderRevenue(tenantId);

            context.status(200).json(Map.of("revenue", revenue, "pendingBookings", pendingBookings, "activeCars", carsInBay));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void getReservations(@NotNull Context context) {
        String tenantId = context.queryParam("tenantId");

        try {
            ArrayList<Reservation> pending = DatabaseEditor.getTenantPendingReservations(tenantId);
            context.status(200).json(pending);
        } catch (SQLException e) {
            e.printStackTrace();
            context.status(500).json(List.of());
        }
    }

    public static void getActiveOrders(@NotNull Context context) {
        String tenantId = context.queryParam("tenantId");

        try {
            ArrayList<Order> orders = DatabaseEditor.getActiveOrders(tenantId);
            context.status(200).json(orders);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void getRecentAdminActivity(@NotNull Context context) {
        context.status(200).json(List.of());
    }

    public static void updateOrderStatus(@NotNull Context context) {
        String id = context.pathParam("id");
        StatusUpdateRequest req = context.bodyAsClass(StatusUpdateRequest.class);

        try {
            DatabaseEditor.updateReservationStatus(id, req.status);
            context.status(200).json(Map.of("success", true, "message", "Status updated"));
        } catch (SQLException e) {
            e.printStackTrace();
            context.status(500).json(Map.of("success", false, "message", "Failed to update status"));
        }
    }

    public static class BookingRequest {
        public String name;
        public String email;
        public String venue;
        public String service;
        public String startDate;
        public String endDate;
        public String paymentMethod;
    }

    public static class LoggedBookingRequest {
        public String venue;
        public String service;
        public String startDate;
        public String endDate;
    }

    public static class LoginRequest {
        public String email;
        public String password;
    }

    public static class RegistrationRequest {
        public String username;
        public String email;
        public String number;
        public String password;
    }

    public static class StatusUpdateRequest {
        public String status;
    }
}