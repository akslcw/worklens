package com.su.worklens_backend.service;

import com.su.worklens_backend.auth.AuthenticatedUser;
import com.su.worklens_backend.dto.EmployeeRequest;
import com.su.worklens_backend.dto.CreateEmployeeResponse;
import com.su.worklens_backend.dto.ResetEmployeePasswordResponse;
import com.su.worklens_backend.entity.Employee;

import java.util.List;

public interface EmployeeService {

    CreateEmployeeResponse createEmployee(EmployeeRequest request, AuthenticatedUser authenticatedUser);

    List<Employee> listEmployees(AuthenticatedUser authenticatedUser);

    Employee getEmployeeById(Long id, AuthenticatedUser authenticatedUser);

    Employee updateEmployee(Long id, EmployeeRequest request, AuthenticatedUser authenticatedUser);

    void deleteEmployee(Long id, AuthenticatedUser authenticatedUser);

    ResetEmployeePasswordResponse resetEmployeePassword(Long id, AuthenticatedUser authenticatedUser);
}
