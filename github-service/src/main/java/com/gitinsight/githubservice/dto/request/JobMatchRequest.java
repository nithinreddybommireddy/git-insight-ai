package com.gitinsight.githubservice.dto.request;

import java.util.List;

/**
 * Request for the AI job-match endpoint: the job description plus the
 * deterministically matched candidates that Gemini should reason about.
 */
public record JobMatchRequest(
        String jobTitle,
        String jobDescription,
        List<String> requiredSkills,
        List<Candidate> candidates
) {

    public record Candidate(
            String username,
            String name,
            String bio,
            int developerScore,
            String level,
            List<String> languages,
            List<String> matchedSkills,
            List<String> missingSkills,
            List<String> topRepos
    ) {
    }
}
