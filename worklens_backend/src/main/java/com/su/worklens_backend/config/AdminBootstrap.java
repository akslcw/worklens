package com.su.worklens_backend.config;

import com.su.worklens_backend.service.PasswordHasher;
import com.su.worklens_backend.service.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Creates the initial MANAGER account from environment variables when the
 * database has no manager yet (WORKLENS_ADMIN_USERNAME / WORKLENS_ADMIN_NAME /
 * WORKLENS_ADMIN_PASSWORD). Without a password the bootstrap is skipped, so
 * nothing changes for existing deployments.
 */
@Component
public class AdminBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrap.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    private final String adminUsername;
    private final String adminName;
    private final String adminPassword;

    public AdminBootstrap(
            JdbcTemplate jdbcTemplate,
            PasswordHasher passwordHasher,
            Clock clock,
            @Value("${worklens.admin.username:}") String adminUsername,
            @Value("${worklens.admin.name:Administrator}") String adminName,
            @Value("${worklens.admin.password:}") String adminPassword
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
        this.adminUsername = adminUsername;
        this.adminName = adminName;
        this.adminPassword = adminPassword;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrapInitialAdministrator() {
        if (adminPassword == null || adminPassword.isBlank()) {
            return;
        }
        PasswordPolicy.requireStrength(adminPassword);
        String username = adminUsername.isBlank() ? "admin" : adminUsername.trim();

        Integer managerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_users WHERE role = 'MANAGER'",
                Integer.class
        );
        if (managerCount != null && managerCount > 0) {
            LOGGER.info("A manager account already exists; skipping administrator bootstrap.");
            return;
        }

        Integer existingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_users WHERE username = ?",
                Integer.class,
                username
        );
        if (existingCount != null && existingCount > 0) {
            throw new IllegalStateException(
                    "WORKLENS_ADMIN_USERNAME '" + username + "' already exists but is not a manager; "
                            + "choose a different username or clear the conflicting account."
            );
        }

        Long employeeId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO employees (name, employee_no, created_at)
                        VALUES (?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                adminName,
                username,
                LocalDateTime.now(clock)
        );

        jdbcTemplate.update(
                """
                        INSERT INTO auth_users (username, password_hash, role, employee_id, must_change_password, created_at)
                        VALUES (?, ?, 'MANAGER', ?, TRUE, ?)
                        """,
                username,
                passwordHasher.hash(adminPassword),
                employeeId,
                LocalDateTime.now(clock)
        );

        LOGGER.info(
                "Created initial administrator account '{}'. It must change its password on first login.",
                username
        );
    }
}
