package com.gitinsight.authservice.dto.response;

import java.util.List;

/**
 * Result of a recruiter job-description file match — a fresh, ranked
 * candidate search scored against the skills required by the job.
 * Optionally enriched with per-candidate AI explanations (Gemini).
 */
public record JobMatchResponse(
        String jobTitle,
        List<String> requiredSkills,
        String source,            // "file" when usernames were uploaded, "saved" for the recruiter's pool
        int total,                // pool size considered
        int processed,            // candidates successfully analyzed
        int failed,               // candidates that could not be fetched/scored
        List<JobMatchCandidate> results,
        boolean aiEnabled,        // true when AI explanations were generated
        String aiModel,           // e.g. "gemini-2.0-flash"
        List<AiExplanation> aiExplanations
) {

    public static JobMatchResponse empty(String source) {
        return new JobMatchResponse("", List.of(), source, 0, 0, 0, List.of(), false, null, List.of());
    }

    /**
     * A single ranked candidate with the job-match breakdown.
     */
    public record JobMatchCandidate(
            String username,
            String name,
            String avatarUrl,
            String bio,
            int developerScore,
            String level,
            int matchScore,          // 0-100 blend of skill match + developer score
            int skillMatchPercent,   // 0-100 share of required skills present
            List<String> matchedSkills,
            List<String> missingSkills,
            List<String> languages,
            List<String> topRepos
    ) {
    }

    /**
     * AI-generated fit explanation for one candidate.
     */
    public record AiExplanation(
            String username,
            int aiRank,              // Gemini's ranking of the candidate for this job
            String fitLabel,         // Strong fit | Good fit | Partial fit | Weak fit
            String explanation,
            List<String> strengths,
            List<String> gaps,
            String recommendation
    ) {
    }
}
