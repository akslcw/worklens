package com.worklens.backend.controller;

import com.worklens.backend.common.result.Result;
import com.worklens.backend.entity.AppCategory;
import com.worklens.backend.service.AppCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class AppCategoryController {

    private final AppCategoryService appCategoryService;

    @GetMapping
    public Result<List<AppCategory>> listAll() {
        return Result.success(appCategoryService.listAll());
    }

    @PostMapping
    public Result<Void> add(@RequestBody AppCategory appCategory) {
        appCategoryService.add(appCategory);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody AppCategory appCategory) {
        appCategoryService.update(appCategory);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        appCategoryService.delete(id);
        return Result.success();
    }
}