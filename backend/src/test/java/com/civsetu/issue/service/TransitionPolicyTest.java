package com.civsetu.issue.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.civsetu.common.domain.Priority;
import com.civsetu.common.error.ApiException;
import com.civsetu.common.geo.GeoPoints;
import com.civsetu.common.security.Caller;
import com.civsetu.issue.entity.Issue;
import com.civsetu.issue.entity.IssueStatus;
import com.civsetu.issue.entity.PrioritySource;

/**
 * Every action from every status, by every kind of caller. Only the combinations listed in
 * {@link TransitionPolicy#RULES} are allowed; Admins can never perform a workflow action.
 */
class TransitionPolicyTest {

    private static final UUID DEPARTMENT = UUID.randomUUID();
    private static final UUID OTHER_DEPARTMENT = UUID.randomUUID();
    private static final UUID REPORTER = UUID.randomUUID();
    private static final UUID AUTHORITY = UUID.randomUUID();

    private static final Caller ASSIGNED_AUTHORITY = new Caller(AUTHORITY, "AUTHORITY", DEPARTMENT);
    private static final Caller COLLEAGUE = new Caller(UUID.randomUUID(), "AUTHORITY", DEPARTMENT);
    private static final Caller OTHER_DEPARTMENT_AUTHORITY = new Caller(AUTHORITY, "AUTHORITY", OTHER_DEPARTMENT);
    private static final Caller REPORTING_CITIZEN = new Caller(REPORTER, "CITIZEN", null);
    private static final Caller OTHER_CITIZEN = new Caller(UUID.randomUUID(), "CITIZEN", null);
    private static final Caller ADMIN = new Caller(UUID.randomUUID(), "ADMIN", null);

    private static Issue issueIn(IssueStatus status) {
        Issue issue = new Issue(REPORTER, "Title", "Description", UUID.randomUUID(), DEPARTMENT, Priority.MEDIUM,
                PrioritySource.RULE, GeoPoints.of(18.5, 73.8), "Address", null);
        ReflectionTestUtils.setField(issue, "status", status);
        ReflectionTestUtils.setField(issue, "assignedAuthorityId", AUTHORITY);
        return issue;
    }

    static Stream<Arguments> everyCombination() {
        List<Arguments> cases = new ArrayList<>();
        for (TransitionPolicy.Action action : TransitionPolicy.Action.values()) {
            for (IssueStatus status : IssueStatus.values()) {
                TransitionPolicy.Rule rule = TransitionPolicy.RULES.get(action);
                Caller rightPerson = rule.actor() == TransitionPolicy.Actor.REPORTER
                        ? REPORTING_CITIZEN : ASSIGNED_AUTHORITY;
                String expected = rule.from().contains(status) ? "OK" : "INVALID_TRANSITION";
                cases.add(Arguments.of(action, status, "right person", rightPerson, expected));
                if (rule.actor() == TransitionPolicy.Actor.DEPARTMENT_AUTHORITY) {
                    // Any Authority of the department may work the issue, not only the assigned lead
                    cases.add(Arguments.of(action, status, "department colleague", COLLEAGUE, expected));
                } else {
                    cases.add(Arguments.of(action, status, "department colleague", COLLEAGUE, "NOT_ALLOWED"));
                }
                for (Caller wrong : List.of(OTHER_DEPARTMENT_AUTHORITY, OTHER_CITIZEN, ADMIN,
                        rule.actor() == TransitionPolicy.Actor.REPORTER ? ASSIGNED_AUTHORITY : REPORTING_CITIZEN)) {
                    cases.add(Arguments.of(action, status, wrong.role() + " " + wrong.id(), wrong, "NOT_ALLOWED"));
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0} from {1} by {2} -> {4}")
    @MethodSource("everyCombination")
    void onlyListedTransitionsByTheRightPersonAreAllowed(TransitionPolicy.Action action, IssueStatus status,
            String who, Caller caller, String expected) {
        Issue issue = issueIn(status);
        if (expected.equals("OK")) {
            assertThatCode(() -> TransitionPolicy.check(action, issue, caller)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> TransitionPolicy.check(action, issue, caller))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> org.assertj.core.api.Assertions.assertThat(e.getCode()).isEqualTo(expected));
        }
    }
}
