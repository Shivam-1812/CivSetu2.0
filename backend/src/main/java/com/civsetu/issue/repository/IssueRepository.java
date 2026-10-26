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

    /**
     * Open issues of a category near a point, most similar title first. Each row is
     * {issue id, distance in metres, title similarity 0-1}. Uses the geography GiST index for the
     * distance filter and pg_trgm for similarity.
     */
    @Query(value = """
            SELECT i.id,
                   ST_Distance(i.location::geography,
                               ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography) AS distance,
                   similarity(lower(i.title), lower(:title)) AS title_similarity
            FROM issues i
            WHERE ST_DWithin(i.location::geography,
                             ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, :radius)
              AND i.category_id = :categoryId
              AND i.status IN ('REPORTED', 'ASSIGNED', 'IN_PROGRESS')
              AND i.created_at >= :since
              AND i.id <> :excludeId
            ORDER BY title_similarity DESC, distance ASC
            LIMIT :maxResults
            """, nativeQuery = true)
    List<Object[]> findSimilar(@Param("latitude") double latitude, @Param("longitude") double longitude,
            @Param("categoryId") UUID categoryId, @Param("title") String title, @Param("radius") double radius,
            @Param("since") Instant since, @Param("excludeId") UUID excludeId, @Param("maxResults") int maxResults);

    /**
     * Claims open issues whose deadline has passed and whose breach alert has not gone out yet, by
     * stamping them in one atomic statement. A claimed issue is never returned again, so the alert is
     * sent exactly once, even across restarts or several servers (SKIP LOCKED avoids waiting on each other).
     */
    @Query(value = """
            UPDATE issues SET breach_notified_at = :now
            WHERE id IN (
                SELECT id FROM issues
                WHERE status IN ('REPORTED', 'ASSIGNED', 'IN_PROGRESS')
                  AND sla_due_at < :now
                  AND breach_notified_at IS NULL
                ORDER BY sla_due_at
                LIMIT :batch
                FOR UPDATE SKIP LOCKED)
            RETURNING id
            """, nativeQuery = true)
    List<UUID> claimBreached(@Param("now") Instant now, @Param("batch") int batch);

    /**
     * Groups open issues with DBSCAN: issues within {@code radius} metres of each other join one
     * cluster, and a cluster needs at least {@code minIssues}. Clustering runs in Web Mercator, which
     * stretches distances by 1/cos(latitude), so the radius is scaled by the area's mean latitude.
     * Each row is {centre latitude, centre longitude, issue count, reporter count}, largest first.
     */
    @Query(value = """
            WITH open_issues AS (
                SELECT reporter_id, location FROM issues
                WHERE status IN ('REPORTED', 'ASSIGNED', 'IN_PROGRESS')
                  AND (:allDepartments OR department_id = :departmentId)
            ), clustered AS (
                SELECT reporter_id, location,
                       ST_ClusterDBSCAN(ST_Transform(location, 3857),
                           :radius / cos(radians((SELECT avg(ST_Y(location)) FROM open_issues))),
                           :minIssues) OVER () AS cluster
                FROM open_issues
            )
            SELECT ST_Y(ST_Centroid(ST_Collect(location))), ST_X(ST_Centroid(ST_Collect(location))),
                   count(*), count(DISTINCT reporter_id)
            FROM clustered
            WHERE cluster IS NOT NULL
            GROUP BY cluster
            ORDER BY count(*) DESC
            LIMIT 50
            """, nativeQuery = true)
    List<Object[]> findClusters(@Param("radius") double radius, @Param("minIssues") int minIssues,
            @Param("allDepartments") boolean allDepartments, @Param("departmentId") UUID departmentId);

    /** Everyone who reported an open issue within {@code radius} metres of a point. */
    @Query(value = """
            SELECT DISTINCT reporter_id FROM issues
            WHERE status IN ('REPORTED', 'ASSIGNED', 'IN_PROGRESS')
              AND ST_DWithin(location::geography,
                             ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, :radius)
              AND (:allDepartments OR department_id = :departmentId)
            """, nativeQuery = true)
    List<UUID> findReportersNear(@Param("latitude") double latitude, @Param("longitude") double longitude,
            @Param("radius") double radius, @Param("allDepartments") boolean allDepartments,
            @Param("departmentId") UUID departmentId);

    /** Demo data only: places a seeded issue in the past (auditing always stamps "now"). */
    @Modifying
    @Query(value = "UPDATE issues SET created_at = :createdAt, updated_at = :createdAt, sla_due_at = :slaDueAt "
            + "WHERE id = :id", nativeQuery = true)
    void backdate(@Param("id") UUID id, @Param("createdAt") Instant createdAt, @Param("slaDueAt") Instant slaDueAt);
}
