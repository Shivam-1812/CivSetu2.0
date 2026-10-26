package com.civsetu.sla.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civsetu.common.domain.Priority;
import com.civsetu.sla.entity.SlaPolicy;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Priority> {
}
