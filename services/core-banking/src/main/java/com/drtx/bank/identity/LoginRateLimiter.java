package com.drtx.bank.identity;

import java.util.concurrent.ConcurrentHashMap;

import com.drtx.bank.shared.error.DomainException;
import com.drtx.bank.shared.error.ErrorCode;
import com.drtx.bank.shared.security.SecurityProperties;

import org.springframework.stereotype.Component;

/** Fixed one-minute window per client IP, in front of per-user lockout. */
// shortcut: in-memory per replica; with several replicas the WAF rate rule (infra/terraform) is the real limit.
@Component
class LoginRateLimiter {

    private record Window(long minute, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxPerMinute;

    LoginRateLimiter(SecurityProperties props) {
        this.maxPerMinute = props.login().maxAttemptsPerMinute();
    }

    void check(String clientKey) {
        long minute = System.currentTimeMillis() / 60_000;
        Window w = windows.merge(clientKey, new Window(minute, 1),
                (old, fresh) -> old.minute() == minute ? new Window(minute, old.count() + 1) : fresh);
        if (w.count() > maxPerMinute) {
            throw new DomainException(ErrorCode.RATE_LIMITED, "Too many login attempts, try again in a minute");
        }
        if (windows.size() > 50_000) {
            windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
        }
    }
}
