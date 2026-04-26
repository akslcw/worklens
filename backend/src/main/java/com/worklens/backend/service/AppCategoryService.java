package com.worklens.backend.service;

import com.worklens.backend.entity.AppCategory;
import java.util.List;

public interface AppCategoryService {
    List<AppCategory> listAll();
    void add(AppCategory appCategory);
    void update(AppCategory appCategory);
    void delete(Long id);
}