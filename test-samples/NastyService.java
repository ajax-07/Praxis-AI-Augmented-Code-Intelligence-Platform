package sample;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deliberately awful code for smoke-testing the Praxis LLM funnel.
 * processOrder() is engineered to trip HIGH_COMPLEXITY (cx >= 10) and
 * LONG_METHOD (> 60 LOC), which pushes its risk score far past the
 * funnel threshold (60) — guaranteeing it is sent to the LLM.
 */
public class NastyService {

    private static NastyService instance;          // also trips SINGLETON (INFO)
    private final Map<String, Object> cache = new HashMap<>();

    private NastyService() { }

    public static NastyService getInstance() {
        if (instance == null) {
            instance = new NastyService();
        }
        return instance;
    }

    public String processOrder(String type, int quantity, double price, String region,
                               boolean express, boolean giftWrap, String couponCode) {
        List<String> log = new ArrayList<>();
        double total = 0;
        if (type == null || type.isEmpty()) {
            return "ERROR: no type";
        }
        if (quantity <= 0) {
            return "ERROR: bad quantity";
        }
        if (type.equals("BOOK")) {
            total = price * quantity;
            if (quantity > 10 && region.equals("EU")) {
                total = total * 0.9;
                log.add("bulk EU discount");
            } else if (quantity > 10) {
                total = total * 0.95;
                log.add("bulk discount");
            }
        } else if (type.equals("ELECTRONICS")) {
            total = price * quantity * 1.2;
            if (express && region.equals("US")) {
                total += 25;
            } else if (express) {
                total += 40;
            }
            for (int i = 0; i < quantity; i++) {
                if (price > 500 || (couponCode != null && couponCode.startsWith("VIP"))) {
                    log.add("insurance item " + i);
                    total += 5;
                }
            }
        } else if (type.equals("FOOD")) {
            total = price * quantity;
            if (region.equals("EU") && !express) {
                total = total * 1.07;
            } else if (region.equals("US") && !express) {
                total = total * 1.05;
            } else {
                total = total * 1.1;
            }
        } else {
            total = price * quantity * 1.5;
            log.add("unknown type surcharge");
        }
        if (giftWrap) {
            total += quantity > 5 ? 10 : 5;
            log.add("gift wrap");
        }
        if (couponCode != null && !couponCode.isEmpty()) {
            if (couponCode.equals("SAVE10")) {
                total = total * 0.9;
            } else if (couponCode.equals("SAVE20") && total > 100) {
                total = total * 0.8;
            } else if (couponCode.startsWith("VIP") && region.equals("EU")) {
                total = total * 0.75;
                log.add("vip eu");
            }
        }
        cache.put(type + ":" + quantity, total);
        String result = "OK total=" + total;
        for (String entry : log) {
            result = result + " [" + entry + "]";
        }
        return result;
    }
}
