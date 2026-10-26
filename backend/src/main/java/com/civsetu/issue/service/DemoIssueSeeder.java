package com.civsetu.issue.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civsetu.common.geo.GeoPoints;
import com.civsetu.department.dto.CategoryResponse;
import com.civsetu.department.service.CategoryService;
import com.civsetu.issue.entity.Issue;
import com.civsetu.issue.entity.PrioritySource;
import com.civsetu.issue.repository.IssueRepository;
import com.civsetu.sla.service.SlaService;
import com.civsetu.user.dto.UserResponse;
import com.civsetu.user.service.UserService;

/**
 * Fills an empty database with sample issues around Pune for demos and frontend work.
 * Runs only with the {@code demo} profile (e.g. SPRING_PROFILES_ACTIVE=dev,demo) and only when
 * there are no issues yet. The demo reporter gets a random password, so it cannot be used to log in.
 */
@Service
@Profile("demo")
class DemoIssueSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoIssueSeeder.class);
    private static final String DEMO_REPORTER_EMAIL = "demo.reporter@example.com";
    private static final List<String> AREAS = List.of("Shivaji Nagar", "Kothrud", "Hadapsar", "Aundh", "Baner",
            "Viman Nagar", "Kasba Peth", "Deccan Gymkhana", "Swargate", "Wakad", "Kharadi", "Camp");
    // Spread of about +/- 6 km around the centre
    private static final double SPREAD_DEGREES = 0.11;

    private final IssueRepository issues;
    private final CategoryService categories;
    private final UserService users;
    private final SlaService sla;
    private final Clock clock;
    private final int count;
    private final double centerLatitude;
    private final double centerLongitude;

    DemoIssueSeeder(IssueRepository issues, CategoryService categories, UserService users, SlaService sla,
            Clock clock,
            @Value("${civsetu.demo.issue-count:40}") int count,
            @Value("${civsetu.demo.center-latitude:18.5204}") double centerLatitude,
            @Value("${civsetu.demo.center-longitude:73.8567}") double centerLongitude) {
        this.issues = issues;
        this.categories = categories;
        this.users = users;
        this.sla = sla;
        this.clock = clock;
        this.count = count;
        this.centerLatitude = centerLatitude;
        this.centerLongitude = centerLongitude;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (issues.count() > 0) {
            log.info("Demo data skipped: the database already has issues");
            return;
        }
        UUID reporterId = users.findByEmail(DEMO_REPORTER_EMAIL)
                .map(UserResponse::id)
                .orElseGet(() -> users.registerCitizen("Demo Reporter", DEMO_REPORTER_EMAIL,
                        UUID.randomUUID().toString(), null).id());

        List<CategoryResponse> active = categories.listActive();
        Random random = new Random(42);
        Instant now = clock.instant();
        for (int i = 0; i < count; i++) {
            CategoryResponse category = active.get(random.nextInt(active.size()));
            String area = AREAS.get(random.nextInt(AREAS.size()));
            double latitude = centerLatitude + (random.nextDouble() - 0.5) * SPREAD_DEGREES;
            double longitude = centerLongitude + (random.nextDouble() - 0.5) * SPREAD_DEGREES;
            Instant reportedAt = now.minus(Duration.ofMinutes(random.nextInt(30 * 24 * 60)));
            Instant dueAt = sla.dueAt(category.defaultPriority(), reportedAt);

            Issue issue = issues.saveAndFlush(new Issue(reporterId, category.label() + " in " + area,
                    "Demo report: " + category.label().toLowerCase() + " noticed by residents in " + area + ".",
                    category.id(), category.departmentId(), category.defaultPriority(), PrioritySource.RULE,
                    GeoPoints.of(latitude, longitude), area + ", Pune", dueAt));
            issues.backdate(issue.getId(), reportedAt, dueAt);
        }
        log.info("Demo data: created {} sample issues around ({}, {})", count, centerLatitude, centerLongitude);
    }
}
