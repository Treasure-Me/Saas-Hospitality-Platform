package Payments;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class PayFastService {

    private static final String MERCHANT_ID = "10000100";
    private static final String MERCHANT_KEY = "46f0cd694581a";
    private static final String PAYFAST_BASE_URL = "https://sandbox.payfast.co.za/eng/process";
    private static final String BACKEND_URL = System.getProperty("BACKEND_URL", System.getenv("BACKEND_URL")) != null
            ? System.getProperty("BACKEND_URL", System.getenv("BACKEND_URL"))
            : "https://zen-backend.onrender.com";
    private static final String FRONTEND_URL = System.getProperty("FRONTEND_URL", System.getenv("FRONTEND_URL")) != null
            ? System.getProperty("FRONTEND_URL", System.getenv("FRONTEND_URL"))
            : "https://zen-63.vercel.app";


    public static String generateCheckoutUrl(String bookingId, double amount, String userEmail, String itemName) {
        try {
            return new StringBuilder(PAYFAST_BASE_URL)
                    .append("?merchant_id=").append(MERCHANT_ID)
                    .append("&merchant_key=").append(MERCHANT_KEY)
                    .append("&return_url=").append(URLEncoder.encode(FRONTEND_URL + "/dashboard/dashboard.html", StandardCharsets.UTF_8))
                    .append("&cancel_url=").append(URLEncoder.encode(FRONTEND_URL + "/book/Book.html", StandardCharsets.UTF_8))
                    .append("&notify_url=").append(URLEncoder.encode(BACKEND_URL + "/api/payments/webhook", StandardCharsets.UTF_8))
                    .append("&email_address=").append(URLEncoder.encode(userEmail, StandardCharsets.UTF_8))
                    .append("&m_payment_id=").append(bookingId)
                    .append("&amount=").append(amount)
                    .append("&item_name=").append(URLEncoder.encode(itemName, StandardCharsets.UTF_8))
                    .toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode payment URL", e);
        }
    }
}