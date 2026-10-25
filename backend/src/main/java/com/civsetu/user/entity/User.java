package com.civsetu.user.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.civsetu.common.persistence.BaseEntity;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    // citext column; the service also stores emails lower-cased so lookups are exact
    @Column(nullable = false, columnDefinition = "citext")
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // Only set for AUTHORITY users (one department each)
    @Column(name = "department_id")
    private UUID departmentId;

    @Column(nullable = false)
    private boolean active = true;

    protected User() {
    }

    public User(String name, String email, String phone, String passwordHash, Role role, UUID departmentId) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.role = role;
        this.departmentId = departmentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public boolean isActive() {
        return active;
    }
}
