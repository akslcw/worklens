package com.su.worklens_backend;

import com.su.worklens_backend.service.impl.Pbkdf2PasswordHasher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Pbkdf2PasswordHasherTests {

    private final Pbkdf2PasswordHasher passwordHasher = new Pbkdf2PasswordHasher();

    @Test
    void hashUsesOwaspRecommendedIterationCountAndRoundTrips() {
        String hash = passwordHasher.hash("Password123!");

        assertThat(hash).startsWith("pbkdf2_sha256$600000$");
        assertThat(passwordHasher.matches("Password123!", hash)).isTrue();
        assertThat(passwordHasher.matches("WrongPassword1!", hash)).isFalse();
    }

    @Test
    void legacyHashesWithLowerIterationCountStillVerify() {
        String legacyHash = "pbkdf2_sha256$120000$d29ya2xlbnMtc2FsdC0wMQ==$y7dDc5YjVRKR+v1GlPwEumSMa6Wa4bMH0h23Tk8Tx64=";

        assertThat(passwordHasher.matches("Password123!", legacyHash)).isTrue();
        assertThat(passwordHasher.matches("WrongPassword1!", legacyHash)).isFalse();
    }
}
