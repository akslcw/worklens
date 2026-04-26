package com.worklens.backend.mapper;

import com.worklens.backend.entity.AppCategory;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AppCategoryMapper {
    AppCategory findByAppName(String appName);
    int insert(AppCategory appCategory);
    List<AppCategory> findAll();
    int update(AppCategory appCategory);
    int deleteById(Long id);
}