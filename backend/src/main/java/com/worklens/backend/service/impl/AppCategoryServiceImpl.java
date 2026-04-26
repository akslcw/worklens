package com.worklens.backend.service.impl;

import com.worklens.backend.entity.AppCategory;
import com.worklens.backend.mapper.AppCategoryMapper;
import com.worklens.backend.service.AppCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppCategoryServiceImpl implements AppCategoryService {

    private final AppCategoryMapper appCategoryMapper;

    @Override
    public List<AppCategory> listAll() {
        return appCategoryMapper.findAll();
    }

    @Override
    public void add(AppCategory appCategory) {
        appCategoryMapper.insert(appCategory);
    }

    @Override
    public void update(AppCategory appCategory) {
        appCategoryMapper.update(appCategory);
    }

    @Override
    public void delete(Long id) {
        appCategoryMapper.deleteById(id);
    }
}