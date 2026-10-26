package com.civsetu.department.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.civsetu.department.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByDefaultDepartmentIdAndActiveTrue(UUID departmentId);

    List<Category> findByActiveTrue(Sort sort);
}
