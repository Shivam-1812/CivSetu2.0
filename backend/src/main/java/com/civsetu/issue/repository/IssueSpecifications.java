package com.civsetu.issue.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import org.locationtech.jts.geom.Geometry;
import org.springframework.data.jpa.domain.Specification;

import com.civsetu.common.geo.GeoPoints;
import com.civsetu.issue.entity.Issue;

/**
 * Turns an {@link IssueFilter} into one JPA query. Each filter maps to an indexed column:
 * status/department/reporter (B-tree), location (GiST), title/description (trigram).
 */
public final class IssueSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private IssueSpecifications() {
    }

    public static Specification<Issue> matching(IssueFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.statuses() != null && !filter.statuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.statuses()));
            }
            if (filter.categoryId() != null) {
                predicates.add(cb.equal(root.get("categoryId"), filter.categoryId()));
            }
            if (filter.departmentId() != null) {
                predicates.add(cb.equal(root.get("departmentId"), filter.departmentId()));
            }
            if (filter.priorities() != null && !filter.priorities().isEmpty()) {
                predicates.add(root.get("priority").in(filter.priorities()));
            }
            if (filter.createdFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.createdFrom()));
            }
            if (filter.createdTo() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), filter.createdTo()));
            }
            if (filter.reporterId() != null) {
                predicates.add(cb.equal(root.get("reporterId"), filter.reporterId()));
            }
            if (filter.text() != null && !filter.text().isBlank()) {
                String pattern = "%" + escapeLike(filter.text().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("description")), pattern, LIKE_ESCAPE)));
            }
            if (filter.hasBoundingBox()) {
                predicates.add(insideBox(cb, root.get("location"), filter));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // ST_Intersects(location, ST_MakeEnvelope(minLng, minLat, maxLng, maxLat, 4326)) uses the GiST index
    private static Predicate insideBox(CriteriaBuilder cb, Expression<Geometry> location, IssueFilter filter) {
        Expression<Geometry> envelope = cb.function("ST_MakeEnvelope", Geometry.class,
                cb.literal(filter.minLongitude()), cb.literal(filter.minLatitude()),
                cb.literal(filter.maxLongitude()), cb.literal(filter.maxLatitude()),
                cb.literal(GeoPoints.WGS84));
        return cb.isTrue(cb.function("ST_Intersects", Boolean.class, location, envelope));
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
