package com.civsetu.department.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.civsetu.department.entity.Department;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    boolean existsByIdAndActiveTrue(UUID id);

    boolean existsByCode(String code);

    List<Department> findByActiveTrue(Sort sort);
}
