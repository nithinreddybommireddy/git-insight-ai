package com.gitinsight.githubservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request for the AI job-match endpoint: the job description plus the
 * deterministically matched candidates that Gemini should reason about.
 *
 * <p>Every field is bounded: this endpoint invokes Gemini (money), and the body
 * itself is parsed into memory before the prompt is built, so oversized input
 * must be rejected at the HTTP layer rather than truncated downstream.
 */
public record JobMatchRequest(
        @Size(max = 200, message = "Job title must be under 200 characters")
        String jobTitle,

        @NotBlank(message = "Job description is required")
        @Size(max = 20_000, message = "Job description must be under 20,000 characters")
        String jobDescription,

        @Size(max = 50, message = "At most 50 required skills are supported")
        List<@Size(max = 60, message = "Skill names must be under 60 characters") String> requiredSkills,

        @Valid
        @Size(max = 25, message = "At most 25 candidates are supported")
        List<Candidate> candidates
) {

    public record Candidate(
            @NotBlank(message = "Candidate username is required")
            @Size(max = 39, message = "Candidate username must be under 39 characters")
            String username,

            @Size(max = 100)
            String name,

            @Size(max = 1000)
            String bio,

            @Min(0) @Max(100)
            int developerScore,

            @Size(max = 50)
            String level,

            @Size(max = 50)
            List<@Size(max = 50) String> languages,

            @Size(max = 50)
            List<@Size(max = 50) String> matchedSkills,

            @Size(max = 50)
            List<@Size(max = 50) String> missingSkills,

            @Size(max = 5)
            List<@Size(max = 100) String> topRepos
    ) {
    }
}
