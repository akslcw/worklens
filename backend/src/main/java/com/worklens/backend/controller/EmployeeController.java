package com.worklens.backend.controller;

import com.worklens.backend.common.result.Result;
import com.worklens.backend.entity.Employee;
import com.worklens.backend.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    public Result<List<Employee>> listAll() {
        return Result.success(employeeService.listAll());
    }

    @GetMapping("/{id}")
    public Result<Employee> getById(@PathVariable Long id) {
        return Result.success(employeeService.getById(id));
    }

    @PostMapping
    public Result<Void> add(@RequestBody Employee employee) {
        employeeService.add(employee);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody Employee employee) {
        employeeService.update(employee);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return Result.success();
    }
}