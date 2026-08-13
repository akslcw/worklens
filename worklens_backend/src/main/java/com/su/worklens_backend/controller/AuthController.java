package com.su.worklens_backend.controller;

import com.su.worklens_backend.dto.CurrentUserResponse;
import com.su.worklens_backend.dto.ChangePasswordRequest;
import com.su.worklens_backend.dto.LoginRequest;
import com.su.worklens_backend.dto.LoginResponse;
import com.su.worklens_backend.dto.PasswordChangeResponse;
import com.su.worklens_backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse currentUser(HttpServletRequest request) {
        return authService.getCurrentUser(request);
    }

    @PostMapping("/change-password")
    public PasswordChangeResponse changePassword(
            HttpServletRequest request,
            @Valid @RequestBody ChangePasswordRequest changePasswordRequest
    ) {
        return authService.changePassword(request, changePasswordRequest);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(request.getHeader("Authorization"));
        return ResponseEntity.noContent().build();
    }
}
