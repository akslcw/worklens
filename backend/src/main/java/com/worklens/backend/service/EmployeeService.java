package com.worklens.backend.service;

import com.worklens.backend.entity.Employee;
import java.util.List;

public interface EmployeeService {
    List<Employee> listAll();
    Employee getById(Long id);
    void add(Employee employee);
    void update(Employee employee);
    void delete(Long id);
}