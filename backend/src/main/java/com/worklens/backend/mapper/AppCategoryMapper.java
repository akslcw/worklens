package com.worklens.backend.mapper;

import com.worklens.backend.entity.AppCategory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppCategoryMapper {
    AppCategory findByAppName(String appName);
    int insert(AppCategory appCategory);
}