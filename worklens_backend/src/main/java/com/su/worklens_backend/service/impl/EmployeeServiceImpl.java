package com.su.worklens_backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.su.worklens_backend.auth.AuthenticatedUser;
import com.su.worklens_backend.dto.EmployeeRequest;
import com.su.worklens_backend.dto.CreateEmployeeResponse;
import com.su.worklens_backend.dto.ResetEmployeePasswordResponse;
import com.su.worklens_backend.entity.AuthUser;
import com.su.worklens_backend.entity.Employee;
import com.su.worklens_backend.mapper.AuthUserMapper;
import com.su.worklens_backend.mapper.EmployeeMapper;
import com.su.worklens_backend.service.AuthService;
import com.su.worklens_backend.service.EmployeeService;
import com.su.worklens_backend.service.PasswordHasher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.List;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    private static final String EMPLOYEE_ROLE = "EMPLOYEE";
    private static final String MANAGER_ROLE = "MANAGER";
    private static final int TEMPORARY_PASSWORD_LENGTH = 20;
    private static final String UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%&*+-=?";
    private static final String PASSWORD_CHARACTERS = UPPERCASE + LOWERCASE + DIGITS + SYMBOLS;

    private final EmployeeMapper employeeMapper;
    private final AuthUserMapper authUserMapper;
    private final AuthService authService;
    private final PasswordHasher passwordHasher;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmployeeServiceImpl(EmployeeMapper employeeMapper, AuthUserMapper authUserMapper, AuthService authService, PasswordHasher passwordHasher) {
        this.employeeMapper = employeeMapper;
        this.authUserMapper = authUserMapper;
        this.authService = authService;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public CreateEmployeeResponse createEmployee(EmployeeRequest request, AuthenticatedUser authenticatedUser) {
        authService.requireRole(authenticatedUser, MANAGER_ROLE);
        Employee employee = new Employee();
        employee.setName(request.getName().trim());
        employee.setEmployeeNo(request.getEmployeeNo().trim());
        employee.setCreatedAt(LocalDateTime.now());
        employeeMapper.insert(employee);

        AuthUser authUser = new AuthUser();
        authUser.setUsername(employee.getEmployeeNo());
        String temporaryPassword = generateTemporaryPassword();
        authUser.setPasswordHash(passwordHasher.hash(temporaryPassword));
        authUser.setRole(EMPLOYEE_ROLE);
        authUser.setEmployeeId(employee.getId());
        authUser.setMustChangePassword(true);
        authUser.setCreatedAt(LocalDateTime.now());
        authUserMapper.insert(authUser);

        return new CreateEmployeeResponse(employee, temporaryPassword);
    }

    @Override
    public List<Employee> listEmployees(AuthenticatedUser authenticatedUser) {
        authService.requireRole(authenticatedUser, MANAGER_ROLE);
        LambdaQueryWrapper<Employee> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(Employee::getId);
        return employeeMapper.selectList(queryWrapper);
    }

    @Override
    public Employee getEmployeeById(Long id, AuthenticatedUser authenticatedUser) {
        authService.requireRole(authenticatedUser, MANAGER_ROLE);
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found");
        }
        return employee;
    }

    @Override
    public Employee updateEmployee(Long id, EmployeeRequest request, AuthenticatedUser authenticatedUser) {
        authService.requireRole(authenticatedUser, MANAGER_ROLE);
        Employee employee = getEmployeeById(id, authenticatedUser);
        employee.setName(request.getName().trim());
        employee.setEmployeeNo(request.getEmployeeNo().trim());
        employeeMapper.updateById(employee);
        return getEmployeeById(id, authenticatedUser);
    }

    @Override
    public void deleteEmployee(Long id, AuthenticatedUser authenticatedUser) {
        authService.requireRole(authenticatedUser, MANAGER_ROLE);
        Employee employee = getEmployeeById(id, authenticatedUser);
        authUserMapper.delete(new LambdaQueryWrapper<AuthUser>().eq(AuthUser::getEmployeeId, employee.getId()));
        employeeMapper.deleteById(employee.getId());
    }

    @Override
    public ResetEmployeePasswordResponse resetEmployeePassword(Long id, AuthenticatedUser authenticatedUser) {
        authService.requireRole(authenticatedUser, MANAGER_ROLE);
        Employee employee = getEmployeeById(id, authenticatedUser);
        AuthUser authUser = authUserMapper.selectOne(
                new LambdaQueryWrapper<AuthUser>().eq(AuthUser::getEmployeeId, employee.getId())
        );
        if (authUser == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee login account not found");
        }

        String temporaryPassword = generateTemporaryPassword();
        authUser.setPasswordHash(passwordHasher.hash(temporaryPassword));
        authUser.setMustChangePassword(true);
        authUserMapper.updateById(authUser);
        return new ResetEmployeePasswordResponse(authUser.getUsername(), temporaryPassword, true);
    }

    private String generateTemporaryPassword() {
        char[] password = new char[TEMPORARY_PASSWORD_LENGTH];
        password[0] = randomCharacter(UPPERCASE);
        password[1] = randomCharacter(LOWERCASE);
        password[2] = randomCharacter(DIGITS);
        password[3] = randomCharacter(SYMBOLS);
        for (int index = 4; index < password.length; index++) {
            password[index] = randomCharacter(PASSWORD_CHARACTERS);
        }
        for (int index = password.length - 1; index > 0; index--) {
            int swapIndex = secureRandom.nextInt(index + 1);
            char value = password[index];
            password[index] = password[swapIndex];
            password[swapIndex] = value;
        }
        return new String(password);
    }

    private char randomCharacter(String characters) {
        return characters.charAt(secureRandom.nextInt(characters.length()));
    }
}
