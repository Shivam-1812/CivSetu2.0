package com.civsetu.issue.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civsetu.issue.entity.Issue;
import com.civsetu.issue.entity.IssueStatus;

public interface IssueRepository extends JpaRepository<Issue, UUID>, JpaSpecificationExecutor<Issue> {

    /** Locks the row so concurrent triage runs or workflow actions on one issue take turns. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Issue i where i.id = :id")
    Optional<Issue> findByIdForUpdate(@Param("id") UUID id);

    /** Open-issue count per Authority, for least-loaded assignment. Authorities with none are absent. */
    @Query("select i.assignedAuthorityId, count(i) from Issue i "
            + "where i.assignedAuthorityId in :authorities and i.status in :open group by i.assignedAuthorityId")
    List<Object[]> countOpenByAuthority(@Param("authorities") Collection<UUID> authorities,
            @Param("open") Collection<IssueStatus> open);

    /** Issues waiting for triage longer than expected (e.g. after a restart). */
    List<Issue> findByStatusAndUpdatedAtBeforeOrderByCreatedAtAsc(IssueStatus status, Instant before, Limit limit);

    /** Departments that have open issues waiting for an Authority (none was active when they were routed). */
    @Query("select distinct i.departmentId from Issue i where i.status in :open and i.assignedAuthorityId is null")
    List<UUID> findDepartmentsWithWaitingIssues(@Param("open") Collection<IssueStatus> open);

    List<Issue> findByDepartmentIdAndStatusInAndAssignedAuthorityIdIsNullOrderByCreatedAtAsc(UUID departmentId,
            Collection<IssueStatus> open, Limit limit);

    @Query("select distinct i.assignedAuthorityId from Issue i "
            + "where i.status in :open and i.assignedAuthorityId is not null")
    List<UUID> findOpenAuthorityIds(@Param("open") Collection<IssueStatus> open);

    List<Issue> findByAssignedAuthorityIdAndStatusIn(UUID authorityId, Collection<IssueStatus> statuses);

    /** Demo data only: places a seeded issue in the past (auditing always stamps "now"). */
    @Modifying
    @Query(value = "UPDATE issues SET created_at = :createdAt, updated_at = :createdAt, sla_due_at = :slaDueAt "
            + "WHERE id = :id", nativeQuery = true)
    void backdate(@Param("id") UUID id, @Param("createdAt") Instant createdAt, @Param("slaDueAt") Instant slaDueAt);
}
