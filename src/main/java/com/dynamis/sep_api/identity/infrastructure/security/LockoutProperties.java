package com.dynamis.sep_api.identity.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Politica de lockout por conta (Sprint 5 Task 5.4). Falhas em {@code windowMinutes} acumulam ate
 * {@code maxAttempts}; ao atingir o limite, a conta fica bloqueada por {@code lockoutMinutes}.
 */
@Component
@ConfigurationProperties(prefix = "app.security.lockout")
public class LockoutProperties {

    private int maxAttempts = 5;
    private int windowMinutes = 15;
    private int lockoutMinutes = 30;

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getWindowMinutes() {
        return windowMinutes;
    }

    public void setWindowMinutes(int windowMinutes) {
        this.windowMinutes = windowMinutes;
    }

    public int getLockoutMinutes() {
        return lockoutMinutes;
    }

    public void setLockoutMinutes(int lockoutMinutes) {
        this.lockoutMinutes = lockoutMinutes;
    }
}
