package com.civsetu.department.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.civsetu.common.persistence.BaseEntity;

/**
 * A municipal department that handles issues. Deactivated rather than deleted, because issues
 * and users keep referring to it.
 */
@Entity
@Table(name = "departments")
public class Department extends BaseEntity {

    // Stable identifier such as ROADS; never changes once created
    @Column(nullable = false, length = 50, updatable = false)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 150)
    private String area;

    @Column(nullable = false)
    private boolean active = true;

    protected Department() {
    }

    public Department(String code, String name, String area) {
        this.code = code;
        this.name = name;
        this.area = area;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
