package com.su.worklens_backend.filter;

import com.su.worklens_backend.auth.AuthenticatedUser;
import com.su.worklens_backend.service.AuthService;
import com.su.worklens_backend.service.impl.AuthServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/auth/login";
    private static final String HEALTH_PATH = "/health";
    private static final String CURRENT_USER_PATH = "/auth/me";
    private static final String CHANGE_PASSWORD_PATH = "/auth/change-password";
    private static final String LOGOUT_PATH = "/auth/logout";
    private static final String EMPLOYEES_PATH_PREFIX = "/employees";
    private static final String USAGE_RECORDS_PATH_PREFIX = "/usage-records";
    private static final String TEAM_USAGE_SUMMARY_PATH = "/team-usage-summary";
    private static final String DETAIL_ACCESS_REQUESTS_PATH_PREFIX = "/detail-access-requests";
    private static final String DETAIL_ACCESS_REQUESTS_TARGETING_ME_PATH = "/detail-access-requests/targeting-me";
    private static final String EMPLOYEE_REPORT_PATH = "/llm/employee-report";
    private static final String EMPLOYEE_REPORT_HISTORY_PATH = "/llm/employee-report-history";
    private static final String TEAM_REPORT_PATH = "/llm/team-report";
    private static final String TEAM_REPORT_HISTORY_PATH = "/llm/team-report-history";
    private static final String MANAGER_ROLE = "MANAGER";
    private static final String EMPLOYEE_ROLE = "EMPLOYEE";

    private final AuthService authService;

    public AuthTokenFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestPath = resolveRequestPath(request);
        if (requestPath == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid encoded request path");
            return;
        }

        String requestMethod = request.getMethod().toUpperCase(Locale.ROOT);
        if (LOGIN_PATH.equals(requestPath) || HEALTH_PATH.equals(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorizationHeader = request.getHeader("Authorization");
        AuthenticatedUser authenticatedUser = authService.resolveAuthenticatedUser(authorizationHeader);
        if (authenticatedUser == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        request.setAttribute(AuthServiceImpl.CURRENT_USER_ATTRIBUTE, authenticatedUser);
        if (authenticatedUser.isMustChangePassword()
                && !CURRENT_USER_PATH.equals(requestPath)
                && !CHANGE_PASSWORD_PATH.equals(requestPath)
                && !LOGOUT_PATH.equals(requestPath)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Password change required");
            return;
        }

        if (requestPath.startsWith(EMPLOYEES_PATH_PREFIX) && !MANAGER_ROLE.equals(authenticatedUser.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Manager role required");
            return;
        }
        if (requestPath.startsWith(DETAIL_ACCESS_REQUESTS_PATH_PREFIX)) {
            if ("POST".equals(requestMethod) && DETAIL_ACCESS_REQUESTS_PATH_PREFIX.equals(requestPath)
                    && !MANAGER_ROLE.equals(authenticatedUser.getRole())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Manager role required");
                return;
            }
            if ("GET".equals(requestMethod) && DETAIL_ACCESS_REQUESTS_TARGETING_ME_PATH.equals(requestPath)
                    && !EMPLOYEE_ROLE.equals(authenticatedUser.getRole())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Employee role required");
                return;
            }
            if ("GET".equals(requestMethod) && !DETAIL_ACCESS_REQUESTS_TARGETING_ME_PATH.equals(requestPath)
                    && !MANAGER_ROLE.equals(authenticatedUser.getRole())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Manager role required");
                return;
            }
            if ("PATCH".equals(requestMethod) && requestPath.endsWith("/decision")
                    && !EMPLOYEE_ROLE.equals(authenticatedUser.getRole())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Employee role required");
                return;
            }
        }
        if (TEAM_USAGE_SUMMARY_PATH.equals(requestPath) && !MANAGER_ROLE.equals(authenticatedUser.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Manager role required");
            return;
        }
        if (EMPLOYEE_REPORT_PATH.equals(requestPath) && !EMPLOYEE_ROLE.equals(authenticatedUser.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Employee role required");
            return;
        }
        if (EMPLOYEE_REPORT_HISTORY_PATH.equals(requestPath) && !EMPLOYEE_ROLE.equals(authenticatedUser.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Employee role required");
            return;
        }
        if (TEAM_REPORT_PATH.equals(requestPath) && !MANAGER_ROLE.equals(authenticatedUser.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Manager role required");
            return;
        }
        if (TEAM_REPORT_HISTORY_PATH.equals(requestPath) && !MANAGER_ROLE.equals(authenticatedUser.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Manager role required");
            return;
        }
        if (requestPath.startsWith(USAGE_RECORDS_PATH_PREFIX) && !EMPLOYEE_ROLE.equals(authenticatedUser.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Employee role required");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Resolves the path used for authorization checks so that it agrees with the
     * path Spring MVC matches against. The container keeps the raw (still
     * percent-encoded) URI in {@link HttpServletRequest#getRequestURI()}, while
     * servlet mapping and Spring's PathPatternParser match the decoded path.
     * Comparing the raw URI therefore allows requests such as GET /%65mployees
     * to skip the prefix checks below and still reach the /employees handler.
     *
     * Encodings that could alter path structure after decoding (encoded slash,
     * backslash, dot, or a second decoding round) are rejected outright.
     */
    private String resolveRequestPath(HttpServletRequest request) {
        String rawUri = request.getRequestURI();
        String lowerUri = rawUri.toLowerCase(Locale.ROOT);
        if (lowerUri.contains("%2f") || lowerUri.contains("%5c")
                || lowerUri.contains("%2e") || lowerUri.contains("%25")) {
            return null;
        }
        try {
            return URLDecoder.decode(rawUri, StandardCharsets.UTF_8)
                    .replaceAll("/{2,}", "/");
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
