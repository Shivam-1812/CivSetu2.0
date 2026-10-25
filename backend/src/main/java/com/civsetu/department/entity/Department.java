package com.civsetu.department.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.civsetu.common.persistence.BaseEntity;

@Entity
@Table(name = "departments")
public class Department extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 150)
    private String area;

    @Column(nullable = false)
    private boolean active = true;

    protected Department() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getArea() {
        return area;
    }

    public boolean isActive() {
        return active;
    }
}
