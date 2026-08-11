package com.gitinsight.githubservice.dto.response;

import com.gitinsight.githubservice.dto.request.CommitDiffReviewRequest;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 6 — AI commit-diff code-quality review (per-file).
 * <p>
 * Gemini reads the actual patches of a commit and returns an overall verdict
 * plus a per-file review (score, summary, issues, suggestions). When no
 * {@code GEMINI_API_KEY} is configured (or the call fails), {@link #deterministic}
 * builds a rule-based review from the diff stats so the UI never breaks.
 */
@Data
public class CommitDiffReviewResponse {

    private boolean aiEnabled;
    private String aiModel;
    private int overallScore;          // 0-100
    private String overallSummary;
    private List<String> keyIssues = new ArrayList<>();
    private List<String> strengths = new ArrayList<>();
    private List<String> recommendations = new ArrayList<>();
    private List<FileReview> fileReviews = new ArrayList<>();

    @Data
    public static class FileReview {
        private String filename;
        private int score;             // 0-100
        private String summary;
        private List<String> issues = new ArrayList<>();
        private List<String> suggestions = new ArrayList<>();
    }

    /**
     * Rule-based fallback review computed from the diff stats (no AI needed).
     * Scores focus on reviewability: balanced file sizes and descriptive,
     * conventional commit messages score highest; mega-diffs and empty commits
     * score low.
     */
    public static CommitDiffReviewResponse deterministic(CommitDiffReviewRequest request) {
        CommitDiffReviewResponse res = new CommitDiffReviewResponse();
        res.setAiEnabled(false);
        res.setAiModel(null);

        List<CommitDiffResponse> commits = request.commits() == null
                ? List.of() : request.commits();

        if (commits.isEmpty()) {
            res.setOverallScore(0);
            res.setOverallSummary("No commit diff data was provided to review.");
            res.getKeyIssues().add("No diffs available — nothing could be analyzed.");
            res.getRecommendations().add("Push code with reviewable, well-scoped commits to enable per-file analysis.");
            return res;
        }

        List<FileReview> fileReviews = new ArrayList<>();
        double fileScoreSum = 0;
        int fileCount = 0;

        for (CommitDiffResponse commit : commits) {
            int messageScore = scoreMessage(commit.getMessage());

            for (CommitDiffResponse.FileDiff file : commit.getFiles()) {
                FileReview fr = new FileReview();
                fr.setFilename(file.getFilename());
                fr.setScore(scoreFile(file));
                fr.setSummary(buildFileSummary(file));
                fr.getIssues().addAll(buildFileIssues(file));
                fr.getSuggestions().addAll(buildFileSuggestions(file));
                fileReviews.add(fr);
                fileScoreSum += fr.getScore();
                fileCount++;
            }

            // Commit-level signals folded into the overall score
            if (commit.getFiles().isEmpty()) {
                FileReview empty = new FileReview();
                empty.setFilename("(no file changes)");
                empty.setScore(50);
                empty.setSummary("This commit contains no file changes — e.g. a merge commit.");
                empty.getIssues().add("No patch to review.");
                fileReviews.add(empty);
                fileScoreSum += 50;
                fileCount++;
            }
            res.getStrengths().addAll(commitStrengths(commit, messageScore));
        }

        double avgFileScore = fileCount == 0 ? 0 : fileScoreSum / fileCount;
        double avgMessageScore = commits.stream()
                .mapToInt(c -> scoreMessage(c.getMessage()))
                .average().orElse(0);
        res.setOverallScore((int) Math.round(avgFileScore * 0.7 + avgMessageScore * 0.3));
        res.setOverallScore(Math.max(0, Math.min(100, res.getOverallScore())));

        if (res.getOverallScore() >= 70) {
            res.setOverallSummary("Reviewable commit: files are reasonably scoped with a descriptive message. "
                    + "Keep changes focused and add tests for modified behavior.");
        } else if (res.getOverallScore() >= 40) {
            res.setOverallSummary("Mixed diff quality: some changes are large or poorly described. "
                    + "Splitting big files into smaller logical changes would make this easier to review.");
        } else {
            res.setOverallSummary("Hard-to-review change: very large diffs and/or vague commit messages. "
                    + "Prefer small, single-purpose commits with conventional, descriptive messages.");
        }

        boolean hasLarge = fileReviews.stream().anyMatch(fr -> fr.getScore() < 60);
        if (hasLarge) {
            res.getKeyIssues().add("One or more files are large or risky to review — see per-file findings.");
        } else {
            res.getKeyIssues().add("No blocking issues detected from diff size alone.");
        }
        if (res.getRecommendations().isEmpty()) {
            res.getRecommendations().add("Add tests for the modified paths.");
            res.getRecommendations().add("Keep future commits under ~400 changed lines for fast, safe reviews.");
        }

        res.setFileReviews(fileReviews);
        return res;
    }

    // ── Deterministic heuristics ──

    private static int scoreMessage(String message) {
        if (message == null || message.isBlank()) return 0;
        String firstLine = message.lines().findFirst().orElse("").trim();
        int score = 50;
        String lower = firstLine.toLowerCase();
        boolean conventional = lower.startsWith("feat:")
                || lower.startsWith("fix:")
                || lower.startsWith("docs:")
                || lower.startsWith("refactor:")
                || lower.startsWith("perf:")
                || lower.startsWith("test:")
                || lower.startsWith("chore:")
                || lower.startsWith("build:")
                || lower.startsWith("ci:")
                || lower.startsWith("style:")
                || lower.startsWith("revert:")
                || lower.contains("(");
        if (conventional) score += 25;
        if (firstLine.length() >= 20) score += 15;
        else if (firstLine.length() >= 10) score += 5;
        if (message.lines().count() > 2) score += 10;
        return Math.max(0, Math.min(100, score));
    }

    private static int scoreFile(CommitDiffResponse.FileDiff file) {
        int changes = file.getChanges() > 0 ? file.getChanges() : file.getAdditions() + file.getDeletions();
        String status = file.getStatus() == null ? "modified" : file.getStatus();

        if (changes == 0) return 55;                 // metadata-only / empty change
        if ("removed".equals(status)) return 70;     // deletions are low-risk
        if (changes <= 200) return 90;               // ideal review size
        if (changes <= 400) return 75;
        if (changes <= 800) return 55;
        return 35;                                   // monolithic
    }

    private static String buildFileSummary(CommitDiffResponse.FileDiff file) {
        int changes = file.getChanges() > 0 ? file.getChanges() : file.getAdditions() + file.getDeletions();
        String status = file.getStatus() == null ? "modified" : file.getStatus();
        return switch (status) {
            case "added" -> String.format("New file with %d line%s added.", changes, changes == 1 ? "" : "s");
            case "removed" -> "File deleted — verify nothing else depends on it.";
            case "renamed" -> "File renamed (content unchanged or lightly touched).";
            default -> String.format("%d line%s changed (+%d / -%d).",
                    changes, changes == 1 ? "" : "s", file.getAdditions(), file.getDeletions());
        };
    }

    private static List<String> buildFileIssues(CommitDiffResponse.FileDiff file) {
        List<String> issues = new ArrayList<>();
        int changes = file.getChanges() > 0 ? file.getChanges() : file.getAdditions() + file.getDeletions();
        if (changes == 0) {
            issues.add("No meaningful content change — verify the change is intentional.");
        } else if (changes > 400) {
            issues.add("Large diff (" + changes + " lines) — hard to review thoroughly and risky to merge.");
        } else if (changes > 200) {
            issues.add("Diff is on the larger side (" + changes + " lines); consider splitting.");
        }
        if ("removed".equals(file.getStatus())) {
            issues.add("Deletion — check for dangling imports, references, or docs.");
        }
        if (issues.isEmpty()) {
            issues.add("No obvious issues from diff size alone — review the logic and confirm tests cover this change.");
        }
        return issues;
    }

    private static List<String> buildFileSuggestions(CommitDiffResponse.FileDiff file) {
        List<String> suggestions = new ArrayList<>();
        int changes = file.getChanges() > 0 ? file.getChanges() : file.getAdditions() + file.getDeletions();
        if (changes > 0) {
            suggestions.add("Add or update tests covering the changed behavior.");
        }
        if (changes > 200) {
            suggestions.add("Split this file into smaller logical changes in future commits.");
        }
        return suggestions;
    }

    private static List<String> commitStrengths(CommitDiffResponse commit, int messageScore) {
        List<String> strengths = new ArrayList<>();
        int totalChanges = commit.getFiles().stream()
                .mapToInt(f -> f.getChanges() > 0 ? f.getChanges() : f.getAdditions() + f.getDeletions())
                .sum();
        if (messageScore >= 70) {
            strengths.add("Descriptive, conventional commit message.");
        } else if (commit.getMessage() != null && commit.getMessage().lines().count() > 2) {
            strengths.add("Commit message includes a body explaining the change.");
        }
        if (totalChanges > 0 && totalChanges <= 400) {
            strengths.add("Change is scoped and reviewable (" + totalChanges + " lines).");
        }
        return strengths;
    }
}
