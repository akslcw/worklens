package com.su.worklens_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {

    @NotBlank(message = "username must not be blank")
    @Size(max = 100, message = "username must not exceed 100 characters")
    private String username;

    @NotBlank(message = "password must not be blank")
    @Size(max = 64, message = "password must not exceed 64 characters")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
