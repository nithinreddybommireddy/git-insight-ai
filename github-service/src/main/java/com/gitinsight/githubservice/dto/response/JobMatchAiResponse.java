package com.gitinsight.githubservice.dto.response;

import java.util.List;

/**
 * AI explanations for a job-match search — one explanation per candidate.
 */
public record JobMatchAiResponse(
        boolean enabled,
        String model,
        List<Explanation> explanations
) {

    public static JobMatchAiResponse disabled() {
        return new JobMatchAiResponse(false, null, List.of());
    }

    public record Explanation(
            String username,
            int aiRank,
            String fitLabel,        // Strong fit | Good fit | Partial fit | Weak fit
            String explanation,     // 2-3 sentences why they fit / don't fit
            List<String> strengths,
            List<String> gaps,
            String recommendation   // Interview / Consider / Skip + why
    ) {
    }
}
