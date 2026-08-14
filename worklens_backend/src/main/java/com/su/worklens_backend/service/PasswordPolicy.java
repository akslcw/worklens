package com.su.worklens_backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared password policy used by the change-password flow and the initial
 * administrator bootstrap.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void requireStrength(String password) {
        if (password == null
                || password.length() < 8
                || !password.matches(".*[a-z].*")
                || !password.matches(".*[A-Z].*")
                || !password.matches(".*\\d.*")
                || !password.matches(".*[^A-Za-z0-9].*")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "New password must be at least 8 characters and include uppercase, lowercase, digit and symbol"
            );
        }
    }

    public static void requireDifferent(String newPassword, String currentPassword) {
        if (newPassword.equals(currentPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be different from the current password");
        }
    }
}
