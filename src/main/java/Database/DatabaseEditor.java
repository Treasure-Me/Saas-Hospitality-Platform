package Database;

import Database.util.DataWrapper.*;
import Database.util.DatabaseConfig;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

public class DatabaseEditor {

    static String[] orderStatuses = new String[]{"cancelled", "open", "kitchen", "served", "closed"};

    public static String addIntoReservations(String tenantId, String name, String email, String type, Timestamp bookingStart, String details) throws SQLException {
        String sweepSql = "DELETE FROM reservations WHERE status = 'pending' AND created_at < (CURRENT_TIMESTAMP - INTERVAL '15 minutes')";
        String insertSql = "INSERT INTO reservations (tenant_id, reservation_type, scheduled_for, status, details) VALUES (?::uuid, ?, ?, 'pending', ?::jsonb) RETURNING id";

        try (Connection conn = DatabaseConfig.getConnection()) {
            try (PreparedStatement sweepStmt = conn.prepareStatement(sweepSql)) {
                sweepStmt.executeUpdate();
            }

            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, tenantId);
                insertStmt.setString(2, type);
                insertStmt.setTimestamp(3, bookingStart);

                String jsonDetails = String.format("{\"name\": \"%s\", \"email\": \"%s\", \"details\": \"%s\"}", name, email, details);
                insertStmt.setString(4, jsonDetails);

                try (ResultSet rs = insertStmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("id");
                    } else {
                        throw new SQLException("Creating booking failed, no ID obtained.");
                    }
                }
            }
        }
    }

    public static String addOrder(String tenantId, Timestamp bookingDate, double price) throws SQLException {
        String sqlOrder = "INSERT INTO orders (tenant_id, customer_id, status, total_amount, created_at) VALUES (?::uuid, ?::uuid, 'open', ?, ?) RETURNING id";
        String orderId = "";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sqlOrder)){

            pstmt.setString(1, tenantId);
            pstmt.setNull(2, java.sql.Types.OTHER);

            pstmt.setDouble(3, price);
            pstmt.setTimestamp(4, bookingDate);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    orderId =  rs.getString("id");
                } else {
                    throw new SQLException("Creating order failed, no ID obtained.");
                }
            }
        }
        return orderId;
    }

    public static void addOrderItems(String orderId, String catalogItemId, int quantity, double price) throws SQLException {
        String sqlOrderItems = "INSERT INTO order_items (order_id, catalog_item_id, quantity, unit_price_at_sale, operational_status) VALUES (?::uuid, ?::uuid, ?, ?, 'pending')";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sqlOrderItems)){

            pstmt.setString(1, orderId);
            pstmt.setString(2, catalogItemId);
            pstmt.setInt(3, quantity);
            pstmt.setDouble(4, price);

            pstmt.executeUpdate();
        }
    }


    public static void addUser(String firstName, String lastName, String email, String phone, String hashedPassword) throws SQLException {
        String sql = "INSERT INTO users (first_name, last_name, email, phone, password_hash) VALUES (?,?,?,?,?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, firstName);
            pstmt.setString(2, lastName);
            pstmt.setString(3, email);
            pstmt.setString(4, phone);
            pstmt.setString(5, hashedPassword);

            pstmt.executeUpdate();
        }
    }

    public static User getUserByEmail(String email) throws SQLException {
        String sql = "SELECT * FROM users WHERE email = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User();
                    user.id = rs.getString("id");
                    user.username = rs.getString("first_name") + " " + rs.getString("last_name");
                    user.email = rs.getString("email");
                    user.number = rs.getString("phone");
                    return user;
                }
            }
        }
        return null;
    }

    public static List<Booking> getUserReservations(String email) throws SQLException {
        String sql = "SELECT id, reservation_type, scheduled_for, details->>'name' AS name, details->>'email' AS email, details->>'details' AS extra_info FROM reservations WHERE details->>'email' = ?";
        List<Booking> bookings = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Booking booking = new Booking();
                    booking.name = rs.getString("name");
                    booking.email = rs.getString("email");
                    booking.venue = rs.getString("extra_info");
                    booking.service = rs.getString("reservation_type");
                    booking.startDate = rs.getString("scheduled_for");
                    booking.endDate = rs.getString("scheduled_for");
                    bookings.add(booking);
                }
            }
        }
        return bookings;
    }

    public static boolean cancelReservation(String bookingId, String userEmail) throws SQLException {
        String sql = "UPDATE reservations SET status = 'cancelled' WHERE id = ?::uuid AND details->>'email' = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, bookingId);
            pstmt.setString(2, userEmail);

            return pstmt.executeUpdate() > 0;
        }
    }

    public static void confirmReservationPayment(String bookingId) throws SQLException {
        String sql = "UPDATE reservations SET status = 'confirmed' WHERE id = ?::uuid";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, bookingId);
            pstmt.executeUpdate();
        }
    }

    public static ArrayList<CatalogItem> getTenantCatalog(String tenantId) throws SQLException {
        String sql = "SELECT c.id, c.name, c.description, c.base_price, c.is_available, cat.type as category_type " +
                "FROM catalog_items c " +
                "INNER JOIN categories cat ON c.category_id = cat.id " +
                "WHERE c.tenant_id = ?::uuid " +
                "AND c.deleted_at IS NULL;";

        ArrayList<CatalogItem> catalogItems = new ArrayList<>();

        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql)) {

            pstmt.setString(1, tenantId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    CatalogItem catalogItem = new CatalogItem();
                    catalogItem.id = rs.getString("id");
                    catalogItem.name = rs.getString("name");
                    catalogItem.category = rs.getString("category_type");
                    catalogItem.description = rs.getString("description");
                    catalogItem.price = rs.getDouble("base_price");
                    catalogItem.isAvailable = rs.getBoolean("is_available");
                    catalogItems.add(catalogItem);
                }
            }
        }
        return catalogItems;
    }

    public static double getCatalogItemPrice(String itemId) throws SQLException {
        String sql = "SELECT base_price FROM catalog_items WHERE id = ?::uuid AND deleted_at IS NULL";
        double price = 0.0;

        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql)) {

            pstmt.setString(1, itemId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    price = rs.getDouble("base_price");
                }
            }
        }
        return price;
    }

    public static String addCatalogItem(String tenantId, CatalogItem item) throws SQLException {
        String sql = "WITH inserted_category AS (\n" +
                "    INSERT INTO categories (tenant_id, type, name) \n" +
                "    VALUES (?::uuid, ?, 'General')\n" +
                "    RETURNING id\n" +
                ")\n" +
                "INSERT INTO catalog_items (tenant_id, category_id, name, description, base_price, is_available)\n" +
                "SELECT ?::uuid, id, ?, ?, ?, ?\n" +
                "FROM inserted_category\n" +
                "RETURNING id;";

        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql)) {

            pstmt.setString(1, tenantId);
            pstmt.setString(2, item.category);
            pstmt.setString(3, tenantId);
            pstmt.setString(4, item.name);
            pstmt.setString(5, item.description != null ? item.description : "");
            pstmt.setDouble(6, item.price);
            pstmt.setBoolean(7, item.isAvailable);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("id");
                }
            }
        }
        return null;
    }

    public static void updateCatalogItemPrice(String catalogItemId, String newPrice) throws SQLException {
        String sql = "UPDATE catalog_items\n" +
                "SET base_price = ?\n" +
                "WHERE id = ?::uuid;\n";

        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql)) {

            pstmt.setDouble(1, Double.parseDouble(newPrice));
            pstmt.setString(2, catalogItemId);
            pstmt.executeUpdate();
        }
    }

    public static double getTotalOrderRevenue(String tenantId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(total_amount), 0.00) AS revenue\n" +
                "FROM orders \n" +
                "WHERE tenant_id = ?::uuid \n" +
                "  AND DATE(created_at) = CURRENT_DATE;";

        double revenue = 0;

        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql)){

            pstmt.setString(1, tenantId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    revenue = rs.getDouble("revenue");
                }
            }
        }
        return revenue;
    }

    public static int getTenantNumberOfPendingReservations(String tenantId) throws SQLException {
        String sql = "SELECT COUNT(*) AS pending_bookings\n" +
                "FROM reservations \n" +
                "WHERE tenant_id = ?::uuid \n" +
                "  AND status = 'pending';";

        int pendingBookings = 0;

        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql)){

            pstmt.setString(1, tenantId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    pendingBookings = rs.getInt("pending_bookings");
                }
            }
        }
        return pendingBookings;
    }

    public static ArrayList<Order> getActiveOrders(String tenantId) throws SQLException {
        String sqlOrders = "SELECT * FROM orders WHERE tenant_id = ?::uuid AND (status = 'open' OR status = 'kitchen')";
        String sqlOrderItems = "SELECT * FROM order_items WHERE order_id = ?::uuid";
        String sqlOrderName = "SELECT first_name, last_name FROM users WHERE id = ?::uuid";
        String sqlItemName = "SELECT name FROM catalog_items WHERE id = ?::uuid";

        Map<String, String[]> orderIdMap = new HashMap<>();
        ArrayList<Order> orders = new ArrayList<>();

        try (Connection connection = DatabaseConfig.getConnection()){

            try(PreparedStatement pstmt = connection.prepareStatement(sqlOrders)){
                pstmt.setString(1, tenantId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        orderIdMap.put(rs.getString("id"), new String[]{rs.getString("status"), rs.getString("created_at"), rs.getString("customer_id")});
                    }
                }
            }

            try(PreparedStatement preparedStatement = connection.prepareStatement(sqlOrderItems)){
                for (Map.Entry<String, String[]> entry: orderIdMap.entrySet()){
                    String currentOrderId = entry.getKey();
                    String[] orderData = entry.getValue();

                    preparedStatement.setString(1, currentOrderId);

                    Order order = new Order();

                    // FIX 2: Set the base order info here so it is never null in the JSON payload!
                    order.orderId = currentOrderId;
                    order.status = orderData[0];
                    order.type = "Take-away";

                    String stringDate = orderData[1];
                    String isoStandard = stringDate.substring(0, 10) + "T" + stringDate.substring(11, 19);
                    order.orderedAt = LocalDateTime.parse(isoStandard).toEpochSecond(ZoneOffset.UTC);

                    String customerId = orderData[2];
                    String name = "Test Bot";

                    if (customerId != null) {
                        try(PreparedStatement psmt = connection.prepareStatement(sqlOrderName)){
                            psmt.setString(1, customerId);
                            try (ResultSet resultSet = psmt.executeQuery()) {
                                if (resultSet.next()) {
                                    name = resultSet.getString("first_name") + " " + resultSet.getString("last_name");
                                }
                            }
                        } catch (Exception e){
                            System.out.println("Error fetching user: " + e.getMessage());
                        }
                    }
                    order.name = name;

                    try (ResultSet rs = preparedStatement.executeQuery()) {
                        while (rs.next()) {
                            String catalogItemId = rs.getString("catalog_item_id");
                            String itemName = "Unknown Item";

                            try(PreparedStatement psmt = connection.prepareStatement(sqlItemName)){
                                psmt.setString(1, catalogItemId);

                                try (ResultSet resultSet = psmt.executeQuery()) {
                                    if (resultSet.next()) {
                                        itemName = resultSet.getString("name");
                                    }
                                }
                            }

                            if (order.orderItems == null) {
                                order.orderItems = new HashMap<>();
                            }
                            order.orderItems.put(itemName, rs.getInt("quantity"));
                        }
                    }

                    orders.add(order);
                }
            }
        }
        return orders;
    }

    public static ArrayList<Reservation> getTenantConfirmedBookings(String tenantId) throws SQLException {
        String sql = "SELECT id, reservation_type, scheduled_for, " +
                "details->>'name' AS guest_name, " +
                "details->>'email' AS guest_email, " +
                "details->>'details' AS extra_info " +
                "FROM reservations " +
                "WHERE tenant_id = ?::uuid " +
                "  AND status = 'confirmed';";

        ArrayList<Reservation> reservations = new ArrayList<>();

        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql)){

            pstmt.setString(1, tenantId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Reservation reservation = new Reservation();

                    reservation.id = rs.getString("id");
                    reservation.type = rs.getString("reservation_type");
                    reservation.datetime = rs.getString("scheduled_for");
                    reservation.name = rs.getString("guest_name");
                    reservation.email = rs.getString("guest_email");
                    reservation.details = rs.getString("extra_info");

                    reservations.add(reservation);
                }
            }
        }
        return reservations;
    }

    public static int getCarsInBay(String tenantId) throws SQLException {
        String sql = "SELECT COUNT(oi.id) AS active_cars\n" +
                "FROM order_items oi\n" +
                "JOIN orders o ON oi.order_id = o.id\n" +
                "WHERE o.tenant_id = ?::uuid \n" +
                "  AND oi.vehicle_id IS NOT NULL \n" +
                "  AND oi.operational_status IN ('pending', 'in_progress');";

        int carsInBay = 0;

        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql)){

            pstmt.setString(1, tenantId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    carsInBay = rs.getInt("active_cars");
                }
            }
        }
        return carsInBay;
    }

    public static void updateReservationStatus(String reservationId, String status) throws SQLException {
        String sql = "UPDATE reservations SET status = ? WHERE id = ?::uuid";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status);
            pstmt.setString(2, reservationId);
            pstmt.executeUpdate();
        }
    }

    public static void updateOrderStatus(String orderId, String status) throws SQLException{
        String sql = "UPDATE orders SET status = ? WHERE id = ?::uuid";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, orderStatuses[Arrays.asList(orderStatuses).indexOf(status)+1]);
            pstmt.setString(2, orderId);
            pstmt.executeUpdate();
        }
    }

    public static ArrayList<Reservation> getTenantPendingReservations(String tenantId) throws SQLException {
        String sql = "SELECT id, reservation_type, scheduled_for, " +
                "details->>'name' AS guest_name, " +
                "details->>'email' AS guest_email, " +
                "details->>'details' AS extra_info " +
                "FROM reservations " +
                "WHERE tenant_id = ?::uuid " +
                "  AND status = 'pending';";

        ArrayList<Reservation> reservations = new ArrayList<>();

        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql)){

            pstmt.setString(1, tenantId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Reservation reservation = new Reservation();

                    reservation.id = rs.getString("id");
                    reservation.type = rs.getString("reservation_type");
                    reservation.datetime = rs.getString("scheduled_for");
                    reservation.name = rs.getString("guest_name");
                    reservation.email = rs.getString("guest_email");
                    reservation.details = rs.getString("extra_info");

                    reservations.add(reservation);
                }
            }
        }
        return reservations;
    }
}