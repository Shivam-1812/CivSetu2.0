package com.civsetu.department.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civsetu.department.entity.Department;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    boolean existsByIdAndActiveTrue(UUID id);
}
