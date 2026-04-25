package com.worklens.backend.mapper;

import com.worklens.backend.entity.Employee;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface EmployeeMapper {
    List<Employee> findAll();
    Employee findById(Long id);
    int insert(Employee employee);
    int update(Employee employee);
    int deleteById(Long id);
}