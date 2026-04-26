package com.worklens.backend.controller;

import com.worklens.backend.common.JwtUtil;
import com.worklens.backend.common.result.Result;
import com.worklens.backend.dto.LoginDTO;
import com.worklens.backend.entity.Admin;
import com.worklens.backend.mapper.AdminMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AdminMapper adminMapper;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody LoginDTO dto) {
        Admin admin = adminMapper.findByUsername(dto.getUsername());
        if (admin == null || !admin.getPassword().equals(dto.getPassword())) {
            return Result.error(401, "用户名或密码错误");
        }
        String token = jwtUtil.generateToken(admin.getUsername());
        Map<String, String> data = new HashMap<>();
        data.put("token", token);
        data.put("username", admin.getUsername());
        return Result.success(data);
    }
}