package Database.util.DataWrapper;

import java.util.HashMap;
import java.util.Map;

public class Order {
    public String orderId;
    public String name;
    public String type;
    public Map<String, Integer> orderItems = new HashMap<>();
    public String status;
    public long orderedAt;
}
