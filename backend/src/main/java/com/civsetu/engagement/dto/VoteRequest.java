package com.civsetu.engagement.dto;

import jakarta.validation.constraints.NotNull;

import com.civsetu.engagement.entity.VoteValue;

public record VoteRequest(@NotNull VoteValue value) {
}
