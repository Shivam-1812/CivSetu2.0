package com.civsetu.department.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.civsetu.common.domain.Priority;
import com.civsetu.common.persistence.BaseEntity;

/**
 * An issue category and its routing rule: new issues in this category go to
 * {@code defaultDepartmentId} with {@code defaultPriority}.
 */
@Entity
@Table(name = "categories")
public class Category extends BaseEntity {

    // Stable identifier such as POTHOLE; clients send this when reporting
    @Column(nullable = false, length = 50, updatable = false)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "default_department_id", nullable = false)
    private UUID defaultDepartmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_priority", nullable = false, length = 10)
    private Priority defaultPriority;

    @Column(nullable = false)
    private boolean active = true;

    protected Category() {
    }

    public Category(String code, String label, UUID defaultDepartmentId, Priority defaultPriority) {
        this.code = code;
        this.label = label;
        this.defaultDepartmentId = defaultDepartmentId;
        this.defaultPriority = defaultPriority;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public UUID getDefaultDepartmentId() {
        return defaultDepartmentId;
    }

    public void setDefaultDepartmentId(UUID defaultDepartmentId) {
        this.defaultDepartmentId = defaultDepartmentId;
    }

    public Priority getDefaultPriority() {
        return defaultPriority;
    }

    public void setDefaultPriority(Priority defaultPriority) {
        this.defaultPriority = defaultPriority;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
