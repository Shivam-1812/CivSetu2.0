package com.civsetu.issue.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.civsetu.common.security.Caller;
import com.civsetu.department.dto.CategoryResponse;
import com.civsetu.department.dto.DepartmentResponse;
import com.civsetu.department.service.CategoryService;
import com.civsetu.department.service.DepartmentService;
import com.civsetu.issue.dto.IssueDetailResponse;
import com.civsetu.issue.dto.IssueSummaryResponse;
import com.civsetu.issue.dto.MapResponse;
import com.civsetu.issue.entity.Issue;
import com.civsetu.user.service.UserService;

/**
 * Builds issue responses, adding category and department names and applying the
 * field-visibility rules for the caller.
 */
@Component
class IssueResponseMapper {

    private final CategoryService categories;
    private final DepartmentService departments;
    private final UserService users;

    IssueResponseMapper(CategoryService categories, DepartmentService departments, UserService users) {
        this.categories = categories;
        this.departments = departments;
        this.users = users;
    }

    /** Category and department names, loaded once per request (both tables are small). */
    record Labels(Map<UUID, CategoryResponse> categories, Map<UUID, DepartmentResponse> departments) {
    }

    Labels labels() {
        return new Labels(categories.lookup(), departments.lookup());
    }

    IssueSummaryResponse summary(Issue issue, Labels labels) {
        CategoryResponse category = labels.categories().get(issue.getCategoryId());
        DepartmentResponse department = labels.departments().get(issue.getDepartmentId());
        return new IssueSummaryResponse(issue.getId(), issue.getTitle(), category.code(), category.label(),
                department.id(), department.name(), issue.getStatus(), issue.getPriority(), issue.getAddress(),
                issue.getLatitude(), issue.getLongitude(), issue.getCreatedAt(), issue.getSlaDueAt());
    }

    MapResponse.Point mapPoint(Issue issue, Labels labels) {
        return new MapResponse.Point(issue.getId(), issue.getLatitude(), issue.getLongitude(),
                labels.categories().get(issue.getCategoryId()).code(), issue.getStatus(), issue.getPriority());
    }

    IssueDetailResponse detail(Issue issue, Caller caller) {
        Labels labels = labels();
        CategoryResponse category = labels.categories().get(issue.getCategoryId());
        DepartmentResponse department = labels.departments().get(issue.getDepartmentId());

        boolean isReporter = caller.is(issue.getReporterId());
        boolean isStaff = caller.isAdmin() || caller.isAuthorityOf(issue.getDepartmentId());
        IssueDetailResponse.Reporter reporter = isReporter || isStaff
                ? users.findById(issue.getReporterId())
                        .map(user -> new IssueDetailResponse.Reporter(user.id(), user.name(), user.email(), user.phone()))
                        .orElse(null)
                : null;

        return new IssueDetailResponse(issue.getId(), issue.getTitle(), issue.getDescription(), category.code(),
                category.label(), department.id(), department.name(), issue.getStatus(), issue.getPriority(),
                issue.getPrioritySource(), issue.getAddress(), issue.getLatitude(), issue.getLongitude(),
                issue.getCreatedAt(), issue.getUpdatedAt(), issue.getSlaDueAt(), issue.getResolvedAt(),
                issue.getDuplicateOfId(), isReporter, reporter,
                isStaff ? issue.getAssignedAuthorityId() : null);
    }
}
