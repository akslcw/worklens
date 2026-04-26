package com.worklens.backend.mapper;

import com.worklens.backend.entity.Admin;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminMapper {
    Admin findByUsername(String username);
}