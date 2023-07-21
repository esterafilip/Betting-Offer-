package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
@RestController
public class BettingBackendApplication {

    private static final long SESSION_TIMEOUT_MS = 10 * 60 * 1000; // 10 minutes in milliseconds
    private Map<Integer, String> sessions = new ConcurrentHashMap<>();
    private Map<Integer, List<StakeEntry>> highStakesMap = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        SpringApplication.run(BettingBackendApplication.class, args);
    }

    // Method to get or create a session for a customer
    @GetMapping("/{customerid}/session")
    public String getSession(@PathVariable int customerid) {
        String existingSession = sessions.get(customerid);
        if (existingSession != null) {
            return existingSession;
        }

        String newSession = generateSessionKey();
        sessions.put(customerid, newSession);
        return newSession;
    }

    // Method to post a customer's stake on a bet offer
    @PostMapping("/{betofferid}/stake")
    public void postStake(
            @PathVariable int betofferid,
            @RequestParam String sessionkey,
            @RequestBody int stake
    ) {
        int customerId = getCustomerIdFromSession(sessionkey);
        if (customerId == -1) {
            // Invalid session, reject the request
            throw new IllegalArgumentException("Invalid session key.");
        }

        List<StakeEntry> highStakes = highStakesMap.computeIfAbsent(betofferid, k -> new ArrayList<>());
        synchronized (highStakes) {
            // Remove existing stake for the same customer
            highStakes.removeIf(entry -> entry.getCustomerId() == customerId);
            highStakes.add(new StakeEntry(customerId, stake));
            highStakes.sort(Collections.reverseOrder()); // Sort in descending order of stakes

            // Keep only the top 20 stakes
            if (highStakes.size() > 20) {
                highStakes.subList(20, highStakes.size()).clear();
            }
        }
    }

    // Method to get a high stakes list for a bet offer
    @GetMapping("/{betofferid}/highstakes")
    public String getHighStakes(@PathVariable int betofferid) {
        List<StakeEntry> highStakes = highStakesMap.getOrDefault(betofferid, new ArrayList<>());
        StringBuilder sb = new StringBuilder();
        for (StakeEntry entry : highStakes) {
            sb.append(entry.getCustomerId()).append("=").append(entry.getStake()).append(",");
        }
        return sb.toString();
    }

    // Helper method to generate a new session key
    private String generateSessionKey() {
        // This is a simple implementation; you can use a more robust way to generate session keys.
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // Helper method to get the customer ID from a session key
    private int getCustomerIdFromSession(String sessionkey) {
        for (Map.Entry<Integer, String> entry : sessions.entrySet()) {
            if (entry.getValue().equals(sessionkey)) {
                return entry.getKey();
            }
        }
        return -1; // Invalid session key
    }

    // Helper class to represent stake entries
    private static class StakeEntry implements Comparable<StakeEntry> {
        private final int customerId;
        private final int stake;

        public StakeEntry(int customerId, int stake) {
            this.customerId = customerId;
            this.stake = stake;
        }

        public int getCustomerId() {
            return customerId;
        }

        public int getStake() {
            return stake;
        }

        @Override
        public int compareTo(StakeEntry o) {
            return Integer.compare(this.stake, o.stake);
        }
    }
}
