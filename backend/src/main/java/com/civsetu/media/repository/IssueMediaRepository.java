package com.civsetu.media.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civsetu.media.entity.IssueMedia;
import com.civsetu.media.entity.MediaKind;

public interface IssueMediaRepository extends JpaRepository<IssueMedia, UUID> {

    List<IssueMedia> findByIssueIdOrderByCreatedAtAsc(UUID issueId);

    List<IssueMedia> findByIssueIdAndKindOrderByCreatedAtAsc(UUID issueId, MediaKind kind);
}
