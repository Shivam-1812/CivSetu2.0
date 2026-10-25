package com.civsetu.department;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface DepartmentRepository extends JpaRepository<Department, UUID> {

    boolean existsByIdAndActiveTrue(UUID id);
}
