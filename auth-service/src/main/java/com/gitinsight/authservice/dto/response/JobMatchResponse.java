package com.gitinsight.authservice.dto.response;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Result of a recruiter job-description file match — a fresh, ranked
 * candidate search scored against the skills required by the job.
 * Optionally enriched with per-candidate AI explanations (Gemini).
 *
 * <p>Skills from the job description are also classified (REQUIRED /
 * PREFERRED / MANDATORY) so the UI can show which gaps matter most and the
 * skill-match percentage can weight mandatory skills double.
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
        List<AiExplanation> aiExplanations,
        Set<String> mandatorySkills,   // JD skills explicitly marked mandatory ("must have")
        Set<String> preferredSkills,   // JD skills explicitly marked preferred ("nice to have")
        Map<String, String> skillCategories // skill → REQUIRED | PREFERRED | MANDATORY
) {

    /** Canonical constructor — keeps invariants (never-null collections). */
    public JobMatchResponse {
        if (mandatorySkills == null) mandatorySkills = Set.of();
        if (preferredSkills == null) preferredSkills = Set.of();
        if (skillCategories == null) skillCategories = Map.of();
    }

    /** Back-compat constructor used by existing tests (no classification). */
    public JobMatchResponse(String jobTitle, List<String> requiredSkills, String source,
                            int total, int processed, int failed,
                            List<JobMatchCandidate> results, boolean aiEnabled, String aiModel,
                            List<AiExplanation> aiExplanations) {
        this(jobTitle, requiredSkills, source, total, processed, failed, results,
                aiEnabled, aiModel, aiExplanations, Set.of(), Set.of(), Map.of());
    }

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
            int skillMatchPercent,   // 0-100 share of required skills present (mandatory skills weighted ×2)
            List<String> matchedSkills,
            List<String> missingSkills,
            List<String> languages,
            List<String> topRepos,
            List<SkillEvidenceView> skillEvidence  // per-skill evidence records (best evidence per skill)
    ) {
    }

    /**
     * Deterministic evidence record for one matched skill.
     *
     * <p>Confidence: HIGH = source/build/config evidence, MEDIUM = documentation
     * evidence, LOW = metadata only. Evidence is recorded per skill from the
     * first (highest-confidence) source that proves it; the AI layer may never
     * override these records.
     */
    public record SkillEvidenceView(
            String skill,
            String confidence,      // HIGH | MEDIUM | LOW
            String repository,
            String module,          // "-" for repository-root evidence
            String file,
            String branch,
            String evidenceType,    // METADATA | DOCUMENTATION | BUILD | CONFIGURATION | SOURCE | PLATFORM
            String evidencePattern  // exact pattern/annotation that matched
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
