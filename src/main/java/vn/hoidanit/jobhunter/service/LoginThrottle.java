package vn.hoidanit.jobhunter.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import vn.hoidanit.jobhunter.util.error.TooManyRequestsException;

/**
 * Slows down password guessing: after {@link #MAX_FAILURES} wrong passwords for the same account within
 * {@link #WINDOW}, further log-in attempts are refused until the window passes. A successful log-in clears the count.
 */
// ponytail: in-memory and per account, so it resets on restart and an attacker can lock a victim out for 15 minutes;
// move to a shared store (and add per-IP limits behind the proxy) if the API is ever run on several instances.
@Component
public class LoginThrottle {
    static final int MAX_FAILURES = 8;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED = 10_000;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    public void check(String username) {
        Deque<Instant> recent = failures.get(key(username));
        if (recent == null) {
            return;
        }
        synchronized (recent) {
            prune(recent);
            if (recent.size() >= MAX_FAILURES) {
                throw new TooManyRequestsException(
                        "Bạn đã nhập sai mật khẩu quá nhiều lần. Vui lòng thử lại sau " + WINDOW.toMinutes() + " phút hoặc đặt lại mật khẩu.");
            }
        }
    }

    public void failed(String username) {
        if (failures.size() > MAX_TRACKED) {
            failures.values().removeIf(deque -> {
                synchronized (deque) {
                    prune(deque);
                    return deque.isEmpty();
                }
            });
        }
        Deque<Instant> recent = failures.computeIfAbsent(key(username), k -> new ArrayDeque<>());
        synchronized (recent) {
            prune(recent);
            recent.addLast(Instant.now());
        }
    }

    public void succeeded(String username) {
        failures.remove(key(username));
    }

    private static void prune(Deque<Instant> recent) {
        Instant cutoff = Instant.now().minus(WINDOW);
        while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
            recent.pollFirst();
        }
    }
}
