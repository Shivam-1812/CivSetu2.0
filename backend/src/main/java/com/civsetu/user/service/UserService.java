package com.civsetu.user.service;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civsetu.common.error.ApiException;
import com.civsetu.common.web.PageResponse;
import com.civsetu.department.service.DepartmentService;
import com.civsetu.user.dto.CreateStaffRequest;
import com.civsetu.user.dto.UpdateProfileRequest;
import com.civsetu.user.dto.UserResponse;
import com.civsetu.user.entity.Role;
import com.civsetu.user.entity.User;
import com.civsetu.user.repository.UserRepository;

/**
 * Public entry point to the user feature: accounts, credentials and roles.
 * Roles are only ever set here on the server, never taken from a public request.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final DepartmentService departments;
    // Checked against when the email is unknown, so both failure paths take the same time
    private final String timingDummyHash;

    UserService(UserRepository users, PasswordEncoder passwordEncoder, DepartmentService departments) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.departments = departments;
        this.timingDummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** Public sign-up: always a Citizen. */
    @Transactional
    public UserResponse registerCitizen(String name, String email, String rawPassword, String phone) {
        return UserResponse.from(create(name, email, rawPassword, phone, Role.CITIZEN, null));
    }

    /** Admin-created Authority or Admin account. */
    @Transactional
    public UserResponse createStaff(CreateStaffRequest request) {
        UUID departmentId = request.departmentId();
        switch (request.role()) {
            case CITIZEN -> throw badRequest("ROLE_NOT_ALLOWED",
                    "Citizens register themselves. Create AUTHORITY or ADMIN accounts here.");
            case AUTHORITY -> {
                if (departmentId == null) {
                    throw badRequest("DEPARTMENT_REQUIRED", "An Authority account needs a department.");
                }
                if (!departments.existsActive(departmentId)) {
                    throw badRequest("DEPARTMENT_NOT_FOUND", "Department was not found or is inactive.");
                }
            }
            case ADMIN -> {
                if (departmentId != null) {
                    throw badRequest("DEPARTMENT_NOT_ALLOWED", "Admin accounts are not tied to a department.");
                }
            }
        }
        return UserResponse.from(create(request.name(), request.email(), request.password(), request.phone(),
                request.role(), departmentId));
    }

    /** Checks email and password; the same error is returned for an unknown email and a wrong password. */
    public UserResponse authenticate(String email, String rawPassword) {
        User user = users.findByEmail(normalizeEmail(email)).orElse(null);
        if (user == null || user.getPasswordHash() == null) {
            passwordEncoder.matches(rawPassword, timingDummyHash);
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "This account has been disabled.");
        }
        return UserResponse.from(user);
    }

    /** The account behind a token or session; fails if it was removed or disabled since. */
    public UserResponse getActiveUser(UUID id) {
        return users.findById(id)
                .filter(User::isActive)
                .map(UserResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_UNAVAILABLE",
                        "This account is no longer available."));
    }

    /** Any account, active or not (e.g. to show who reported an issue). */
    public Optional<UserResponse> findById(UUID id) {
        return users.findById(id).map(UserResponse::from);
    }

    public Optional<UserResponse> findByEmail(String email) {
        return users.findByEmail(normalizeEmail(email)).map(UserResponse::from);
    }

    @Transactional
    public UserResponse updateProfile(UUID id, UpdateProfileRequest request) {
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (request.name() != null) {
            user.setName(request.name().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        return UserResponse.from(user);
    }

    public PageResponse<UserResponse> list(Role role, Pageable pageable) {
        // Fixed sort: client-supplied sort properties are ignored
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> result = role == null ? users.findAll(page) : users.findByRole(role, page);
        return PageResponse.of(result, UserResponse::from);
    }

    public boolean hasAdmin() {
        return users.existsByRole(Role.ADMIN);
    }

    /** Creates the first Admin only when none exists yet. Returns true if an account was created. */
    @Transactional
    public boolean ensureBootstrapAdmin(String name, String email, String rawPassword) {
        if (users.existsByRole(Role.ADMIN)) {
            return false;
        }
        create(name, email, rawPassword, null, Role.ADMIN, null);
        return true;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private User create(String name, String email, String rawPassword, String phone, Role role, UUID departmentId) {
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByEmail(normalizedEmail)) {
            throw emailTaken();
        }
        User user = new User(name.trim(), normalizedEmail, phone, passwordEncoder.encode(rawPassword), role,
                departmentId);
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent sign-up for the same email
            throw emailTaken();
        }
    }

    private static ApiException emailTaken() {
        return ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists.");
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.");
    }

    private static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
