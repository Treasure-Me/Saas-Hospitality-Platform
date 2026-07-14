package Database.util;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConfig {

    // Final fallback for local development if no environment variables exist
    private static final String LOCAL_URL = "jdbc:postgresql://localhost:5432/ZEN_Local";
    private static final String Schema_File = "../resources/Database/Schema/zen_63_saas_database_schema.sql";

    public static void main(String[] args) {
        loadEnvironment();
        initializeDatabase();
    }

    /**
     * Loads .env variables into System properties for local development.
     * On Cloud platforms, ignoreIfMissing() ensures it uses the dashboard variables.
     */
    public static void loadEnvironment() {
        Dotenv dotenv = Dotenv.configure()
                .directory("./src/main/resources/Security/")
                .ignoreIfMissing()
                .load();

        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
    }

    public static void initializeDatabase() {
        try (Connection connection = getConnection()) {
            System.out.println("✅ Connected to: " + connection.getMetaData().getURL());

            if (!Files.exists(Paths.get(Schema_File))) {
                System.err.println("❌ Schema file not found: " + Schema_File);
                createDefaultSchema(connection);
                return;
            }

            String sqlScript = Files.readString(Paths.get(Schema_File));
            executeSqlScript(connection, sqlScript);

            System.out.println("✅ Database schema has been applied successfully");
        } catch (SQLException e) {
            System.err.println("❌ Database fail: " + e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void executeSqlScript(Connection connection, String sqlScript) {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sqlScript);
            System.out.println("✅ SQL file executed successfully.");
        } catch (SQLException e) {
            System.err.println("✗ Failed to execute SQL file.");
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void createDefaultSchema(Connection connection) throws SQLException {
        String schema = """
               -- ==========================================
             -- 1. TABLES & CONSTRAINTS (The Foundation)
             -- ==========================================
             CREATE TABLE IF NOT EXISTS Bookings (
                 id SERIAL PRIMARY KEY,
                 Name TEXT NOT NULL,
                 Email TEXT NOT NULL,
                 Venue TEXT,
                 Service TEXT,
                 BOOKING_START TIMESTAMP,
                 BOOKING_END TIMESTAMP,
                 BOOKED_DATE TIMESTAMP DEFAULT (CURRENT_TIMESTAMP + INTERVAL '2 hours'),
                 CONSTRAINT valid_time_range CHECK (BOOKING_END > BOOKING_START)
             );
             
             CREATE INDEX IF NOT EXISTS idx_venue ON Bookings(Venue);
             CREATE INDEX IF NOT EXISTS idx_booking_start ON Bookings(BOOKING_START);
             
             
             CREATE TABLE IF NOT EXISTS Users (
                  ID SERIAL PRIMARY KEY,
                  Username TEXT NOT NULL,
                  Email TEXT NOT NULL,
                  Number TEXT NOT NULL,
                  Password TEXT NOT NULL,
                  Saved_Date TIMESTAMP DEFAULT (CURRENT_TIMESTAMP + INTERVAL '2 hours'),
             
                 -- These two lines replace the entire registration trigger!
                  CONSTRAINT unique_email UNIQUE (Email)
             --      CONSTRAINT unique_username UNIQUE (Number)
             );
             
             
             -- ==========================================
             -- 2. FUNCTIONS (The Business Logic)
             -- ==========================================
             
             -- Function 2A: Prevent double bookings with a 1-hour buffer
             CREATE OR REPLACE FUNCTION check_booking_overlap()
                 RETURNS TRIGGER AS $$
             DECLARE
                 conflict_count INT;
             BEGIN
                 SELECT COUNT(*)
                 INTO conflict_count
                 FROM Bookings
                 WHERE Venue = NEW.Venue
                   AND id != COALESCE(NEW.id, -1)
                   AND NEW.BOOKING_START < (BOOKING_END + INTERVAL '1 hour')
                   AND (NEW.BOOKING_END + INTERVAL '1 hour') > BOOKING_START;
             
                 IF conflict_count > 0 THEN
                     RAISE EXCEPTION 'Double Booking Detected: Please leave a 1-hour gap between sessions at %', NEW.Venue
                         USING ERRCODE = 'P0001';
                 END IF;
             
                 RETURN NEW;
             END;
             $$ LANGUAGE plpgsql;
             
             
             -- Function 2B: Verify login credentials
             CREATE OR REPLACE FUNCTION verify_user_login(p_email TEXT, p_password TEXT)
                 RETURNS BOOLEAN AS $$
             DECLARE
                 is_valid BOOLEAN;
             BEGIN
                 SELECT EXISTS (
                     SELECT 1
                     FROM Users
                     WHERE Email = p_email
                       AND Password = p_password
                 ) INTO is_valid;
             
                 RETURN is_valid;
             END;
             $$ LANGUAGE plpgsql;
             
             
             -- ==========================================
             -- 3. TRIGGERS (The Event Listeners)
             -- ==========================================
             DROP TRIGGER IF EXISTS enforce_booking_rules ON Bookings;
             
             CREATE TRIGGER enforce_booking_rules
                 BEFORE INSERT OR UPDATE ON Bookings
                 FOR EACH ROW
             EXECUTE FUNCTION check_booking_overlap();
        """;

        try (Statement statement = connection.createStatement()) {
            statement.execute(schema);
            System.out.println("✅ Schema verified.");
        }
    }

    /**
     * Core connection logic. Priority: System Property (.env) > Env Variable (Cloud) > Local Fallback.
     */
    public static Connection getConnection() throws SQLException {
        String url = System.getProperty("DATABASE_URL", System.getenv("DATABASE_URL"));
        String user = System.getProperty("DB_USER", System.getenv().getOrDefault("DB_USER", "postgres"));
        String pass = System.getProperty("DB_PASSWORD", System.getenv().getOrDefault("DB_PASSWORD", ""));

        if (url == null || url.isEmpty()) {
            url = LOCAL_URL;
        }

        // Professional Caution: Fix common protocol mismatch from cloud providers
        if (url.startsWith("postgres://")) {
            url = url.replace("postgres://", "jdbc:postgresql://");
        }

        return DriverManager.getConnection(url, user, pass);
    }
}