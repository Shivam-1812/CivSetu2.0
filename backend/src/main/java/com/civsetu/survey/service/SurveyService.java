package com.civsetu.survey.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civsetu.audit.service.AuditService;
import com.civsetu.common.error.ApiException;
import com.civsetu.common.geo.GeoPoints;
import com.civsetu.common.security.Caller;
import com.civsetu.issue.dto.IssueCluster;
import com.civsetu.issue.service.IssueService;
import com.civsetu.survey.dto.CreateSurveyRequest;
import com.civsetu.survey.dto.SubmitAnswersRequest;
import com.civsetu.survey.dto.SurveyCreatedEvent;
import com.civsetu.survey.dto.SurveyDetailResponse;
import com.civsetu.survey.dto.SurveyResultsResponse;
import com.civsetu.survey.entity.QuestionType;
import com.civsetu.survey.entity.Survey;
import com.civsetu.survey.entity.SurveyAnswer;
import com.civsetu.survey.entity.SurveyQuestion;
import com.civsetu.survey.entity.SurveySubmission;
import com.civsetu.survey.repository.SurveyAnswerRepository;
import com.civsetu.survey.repository.SurveyRepository;
import com.civsetu.survey.repository.SurveySubmissionRepository;

/**
 * Area surveys: an Authority (for their department's issues) or an Admin picks a cluster of open
 * issues, and everyone who reported one there is invited. Each invited person can answer once.
 */
@Service
@Transactional(readOnly = true)
public class SurveyService {

    private static final Duration MAX_OPEN_PERIOD = Duration.ofDays(90);

    private final SurveyRepository surveys;
    private final SurveySubmissionRepository submissions;
    private final SurveyAnswerRepository answers;
    private final IssueService issues;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    SurveyService(SurveyRepository surveys, SurveySubmissionRepository submissions, SurveyAnswerRepository answers,
            IssueService issues, AuditService audit, ApplicationEventPublisher events, Clock clock) {
        this.surveys = surveys;
        this.submissions = submissions;
        this.answers = answers;
        this.issues = issues;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    /** Clusters of open issues; an Authority only sees its own department's issues. */
    public List<IssueCluster> clusters(Caller staff, int radiusMeters, int minIssues) {
        return issues.clusters(radiusMeters, minIssues, scope(staff));
    }

    @Transactional
    public SurveyDetailResponse create(Caller staff, CreateSurveyRequest request) {
        Instant now = clock.instant();
        if (request.closesAt().isAfter(now.plus(MAX_OPEN_PERIOD))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CLOSES_TOO_LATE",
                    "A survey can stay open for at most " + MAX_OPEN_PERIOD.toDays() + " days.");
        }
        List<UUID> invited = issues.reportersNear(request.centerLatitude(), request.centerLongitude(),
                request.radiusMeters(), scope(staff));
        if (invited.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_REPORTERS_IN_AREA",
                    "Nobody has reported an open issue in this area, so there is no one to ask.");
        }
        List<SurveyQuestion> questions = new ArrayList<>();
        for (int i = 0; i < request.questions().size(); i++) {
            CreateSurveyRequest.Question question = request.questions().get(i);
            questions.add(new SurveyQuestion(i + 1, question.text().trim(), question.type()));
        }
        Survey survey = surveys.save(new Survey(request.title().trim(), trim(request.description()), staff.id(),
                scope(staff), GeoPoints.of(request.centerLatitude(), request.centerLongitude()),
                request.radiusMeters(), request.closesAt(), questions));
        invited.forEach(userId -> surveys.addTarget(survey.getId(), userId));

        audit.record(staff, "SURVEY_CREATED", "SURVEY", survey.getId(), "invited=" + invited.size());
        events.publishEvent(new SurveyCreatedEvent(survey.getId(), survey.getTitle(), survey.getClosesAt(), invited));
        return detail(survey, staff);
    }

    /** Surveys the citizen is invited to, newest first. */
    public List<SurveyDetailResponse> invitations(Caller citizen) {
        return surveys.findInvitedTo(citizen.id()).stream().map(survey -> detail(survey, citizen)).toList();
    }

    /** Visible to invited people, the owner and Admins; to anyone else it does not exist. */
    public SurveyDetailResponse get(UUID surveyId, Caller caller) {
        Survey survey = load(surveyId);
        if (!canManage(survey, caller) && !surveys.isTarget(surveyId, caller.id())) {
            throw ApiException.notFound("Survey");
        }
        return detail(survey, caller);
    }

    @Transactional
    public SurveyDetailResponse respond(UUID surveyId, Caller citizen, SubmitAnswersRequest request) {
        Survey survey = load(surveyId);
        if (!surveys.isTarget(surveyId, citizen.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_INVITED", "You were not invited to this survey.");
        }
        if (!survey.isAcceptingAnswers(clock.instant())) {
            throw ApiException.conflict("SURVEY_CLOSED", "This survey is closed.");
        }
        if (submissions.existsBySurveyIdAndUserId(surveyId, citizen.id())) {
            throw alreadyResponded();
        }
        Map<UUID, SubmitAnswersRequest.Answer> given = byQuestion(request);
        List<Checked> rows = checkedAnswers(survey, given);

        SurveySubmission submission;
        try {
            submission = submissions.saveAndFlush(new SurveySubmission(surveyId, citizen.id()));
        } catch (DataIntegrityViolationException e) {
            // Two submissions raced; the database allowed only one
            throw alreadyResponded();
        }
        rows.forEach(row -> answers.save(new SurveyAnswer(submission.getId(), row.questionId(), row.rating(),
                row.yesNo(), row.text())));
        return detail(survey, citizen);
    }

    /** Aggregated results for the owner and Admins. */
    public SurveyResultsResponse results(UUID surveyId, Caller caller) {
        Survey survey = load(surveyId);
        requireManager(survey, caller);
        Map<UUID, List<SurveyAnswer>> byQuestion = answers.findByQuestionIdIn(
                        survey.getQuestions().stream().map(SurveyQuestion::getId).toList()).stream()
                .collect(Collectors.groupingBy(SurveyAnswer::getQuestionId));

        List<SurveyResultsResponse.QuestionResult> results = new ArrayList<>();
        for (SurveyQuestion question : survey.getQuestions()) {
            List<SurveyAnswer> given = byQuestion.getOrDefault(question.getId(), List.of());
            results.add(switch (question.getType()) {
                case RATING -> {
                    Map<Integer, Long> counts = new LinkedHashMap<>();
                    for (int rating = 1; rating <= 5; rating++) {
                        int value = rating;
                        counts.put(rating, given.stream().filter(a -> a.getRating() == value).count());
                    }
                    Double average = given.isEmpty() ? null
                            : Math.round(given.stream().mapToInt(SurveyAnswer::getRating).average().orElse(0) * 100)
                                    / 100.0;
                    yield new SurveyResultsResponse.QuestionResult(question.getId(), question.getText(),
                            question.getType(), given.size(), average, counts, null, null, null);
                }
                case YES_NO -> new SurveyResultsResponse.QuestionResult(question.getId(), question.getText(),
                        question.getType(), given.size(), null, null,
                        given.stream().filter(SurveyAnswer::getYesNo).count(),
                        given.stream().filter(a -> !a.getYesNo()).count(), null);
                case TEXT -> new SurveyResultsResponse.QuestionResult(question.getId(), question.getText(),
                        question.getType(), given.size(), null, null, null, null,
                        given.stream().map(SurveyAnswer::getText).toList());
            });
        }
        return new SurveyResultsResponse(survey.getId(), survey.getTitle(), surveys.countTargets(surveyId),
                submissions.countBySurveyId(surveyId), results);
    }

    @Transactional
    public SurveyDetailResponse close(UUID surveyId, Caller caller) {
        Survey survey = load(surveyId);
        requireManager(survey, caller);
        survey.close(clock.instant());
        audit.record(caller, "SURVEY_CLOSED", "SURVEY", surveyId, null);
        return detail(survey, caller);
    }

    /** Checks every question is answered in the right form; TEXT answers are optional. */
    /** An answer that passed the checks, before it is saved. */
    private record Checked(UUID questionId, Integer rating, Boolean yesNo, String text) {
    }

    private static List<Checked> checkedAnswers(Survey survey, Map<UUID, SubmitAnswersRequest.Answer> given) {
        Map<UUID, SurveyQuestion> questions = survey.getQuestions().stream()
                .collect(Collectors.toMap(SurveyQuestion::getId, q -> q));
        for (UUID questionId : given.keySet()) {
            if (!questions.containsKey(questionId)) {
                throw badAnswer("UNKNOWN_QUESTION", "Question " + questionId + " is not part of this survey.");
            }
        }
        List<Checked> rows = new ArrayList<>();
        for (SurveyQuestion question : survey.getQuestions()) {
            SubmitAnswersRequest.Answer answer = given.get(question.getId());
            if (question.getType() == QuestionType.TEXT) {
                if (answer != null && answer.text() != null && !answer.text().isBlank()) {
                    rows.add(new Checked(question.getId(), null, null, answer.text().trim()));
                }
            } else if (question.getType() == QuestionType.RATING) {
                if (answer == null || answer.rating() == null) {
                    throw badAnswer("ANSWER_REQUIRED", "Question " + question.getPosition() + " needs a rating 1-5.");
                }
                rows.add(new Checked(question.getId(), answer.rating(), null, null));
            } else {
                if (answer == null || answer.yesNo() == null) {
                    throw badAnswer("ANSWER_REQUIRED", "Question " + question.getPosition() + " needs yes or no.");
                }
                rows.add(new Checked(question.getId(), null, answer.yesNo(), null));
            }
        }
        return rows;
    }

    private static Map<UUID, SubmitAnswersRequest.Answer> byQuestion(SubmitAnswersRequest request) {
        Map<UUID, SubmitAnswersRequest.Answer> given = new HashMap<>();
        for (SubmitAnswersRequest.Answer answer : request.answers()) {
            if (given.put(answer.questionId(), answer) != null) {
                throw badAnswer("DUPLICATE_ANSWER", "Question " + answer.questionId() + " is answered twice.");
            }
        }
        return given;
    }

    private SurveyDetailResponse detail(Survey survey, Caller viewer) {
        List<SurveyDetailResponse.Question> questions = survey.getQuestions().stream()
                .map(q -> new SurveyDetailResponse.Question(q.getId(), q.getPosition(), q.getText(), q.getType()))
                .toList();
        boolean answered = !viewer.isAnonymous() && submissions.existsBySurveyIdAndUserId(survey.getId(), viewer.id());
        return new SurveyDetailResponse(survey.getId(), survey.getTitle(), survey.getDescription(), survey.getStatus(),
                survey.getClosesAt(), survey.getCenterLatitude(), survey.getCenterLongitude(),
                survey.getRadiusMeters(), surveys.countTargets(survey.getId()),
                submissions.countBySurveyId(survey.getId()), answered, questions);
    }

    private Survey load(UUID surveyId) {
        return surveys.findById(surveyId).orElseThrow(() -> ApiException.notFound("Survey"));
    }

    private static boolean canManage(Survey survey, Caller caller) {
        return caller.isAdmin() || caller.is(survey.getCreatedBy());
    }

    private static void requireManager(Survey survey, Caller caller) {
        if (!canManage(survey, caller)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_ALLOWED",
                    "Only the survey's creator and Admins can do this.");
        }
    }

    /** An Authority works within its department; an Admin across all (null). */
    private static UUID scope(Caller staff) {
        return staff.isAdmin() ? null : staff.departmentId();
    }

    private static ApiException alreadyResponded() {
        return ApiException.conflict("ALREADY_RESPONDED", "You have already answered this survey.");
    }

    private static ApiException badAnswer(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static String trim(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
