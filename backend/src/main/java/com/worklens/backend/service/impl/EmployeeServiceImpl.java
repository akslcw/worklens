package com.worklens.backend.service.impl;

import com.worklens.backend.common.exception.BusinessException;
import com.worklens.backend.entity.Employee;
import com.worklens.backend.mapper.EmployeeMapper;
import com.worklens.backend.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeMapper employeeMapper;

    @Override
    public List<Employee> listAll() {
        return employeeMapper.findAll();
    }

    @Override
    public Employee getById(Long id) {
        Employee employee = employeeMapper.findById(id);
        if (employee == null) {
            throw new BusinessException("员工不存在");
        }
        return employee;
    }

    @Override
    public void add(Employee employee) {
        employeeMapper.insert(employee);
    }

    @Override
    public void update(Employee employee) {
        employeeMapper.update(employee);
    }

    @Override
    public void delete(Long id) {
        employeeMapper.deleteById(id);
    }
}