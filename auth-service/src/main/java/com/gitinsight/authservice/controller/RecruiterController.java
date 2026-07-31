package com.gitinsight.authservice.controller;

import com.gitinsight.authservice.dto.response.JobMatchResponse;
import com.gitinsight.authservice.entity.RecruiterNote;
import com.gitinsight.authservice.entity.SavedCandidate;
import com.gitinsight.authservice.entity.User;
import com.gitinsight.authservice.repository.RecruiterNoteRepository;
import com.gitinsight.authservice.repository.SavedCandidateRepository;
import com.gitinsight.authservice.repository.UserRepository;
import com.gitinsight.authservice.service.JobMatcherService;
import com.gitinsight.common.dto.response.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recruiter")
@PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
public class RecruiterController {

    private static final long MAX_JOB_DESCRIPTION_BYTES = 5L * 1024 * 1024; // 5 MB

    private final SavedCandidateRepository savedCandidateRepository;
    private final RecruiterNoteRepository recruiterNoteRepository;
    private final UserRepository userRepository;
    private final JobMatcherService jobMatcherService;

    public RecruiterController(SavedCandidateRepository savedCandidateRepository,
                                RecruiterNoteRepository recruiterNoteRepository,
                                UserRepository userRepository,
                                JobMatcherService jobMatcherService) {
        this.savedCandidateRepository = savedCandidateRepository;
        this.recruiterNoteRepository = recruiterNoteRepository;
        this.userRepository = userRepository;
        this.jobMatcherService = jobMatcherService;
    }

    private User getRecruiter(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Recruiter not found"));
    }

    // ── Job-Description Match (file-driven candidate search) ──

    /**
     * Upload a job description file (.txt/.md/.pdf) and optionally a CSV/TXT of
     * GitHub usernames. Runs a fresh candidate search ranked by job fit.
     * Without a usernames file, the recruiter's saved candidates are used.
     */
    @PostMapping(value = "/match", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<JobMatchResponse>> matchByJobDescription(
            Authentication auth,
            @RequestPart("file") MultipartFile jobDescriptionFile,
            @RequestPart(value = "usernames", required = false) MultipartFile usernamesFile,
            @RequestParam(value = "ai", required = false, defaultValue = "false") boolean includeAi) {

        try {
            if (jobDescriptionFile == null || jobDescriptionFile.isEmpty()) {
                return badRequest("Job description file is empty.");
            }
            if (jobDescriptionFile.getSize() > MAX_JOB_DESCRIPTION_BYTES) {
                return badRequest("Job description file must be under 5 MB.");
            }

            String jdText = jobMatcherService.extractText(
                    jobDescriptionFile.getOriginalFilename(), jobDescriptionFile.getBytes());
            if (jdText == null || jdText.isBlank()) {
                return badRequest("Could not read any text from the job description file.");
            }

            User recruiter = getRecruiter(auth);
            List<String> usernames;
            String source;
            if (usernamesFile != null && !usernamesFile.isEmpty()) {
                if (usernamesFile.getSize() > MAX_JOB_DESCRIPTION_BYTES) {
                    return badRequest("Usernames file must be under 5 MB.");
                }
                usernames = jobMatcherService.parseUsernames(
                        jobMatcherService.readText(usernamesFile.getBytes()));
                source = "file";
            } else {
                usernames = savedCandidateRepository.findByRecruiterOrderByCreatedAtDesc(recruiter)
                        .stream()
                        .map(SavedCandidate::getCandidateUsername)
                        .toList();
                source = "saved";
            }

            List<String> pool = usernames.stream()
                    .distinct()
                    .limit(JobMatcherService.MAX_CANDIDATES)
                    .toList();

            if (pool.isEmpty()) {
                return ResponseEntity.ok(new ApiResponse<>(true,
                        "No candidates to match — upload a usernames file or save candidates first.",
                        JobMatchResponse.empty(source)));
            }

            JobMatchResponse response = jobMatcherService.match(jdText, pool, source, includeAi);
            return ResponseEntity.ok(new ApiResponse<>(true,
                    "Job match completed for " + response.processed() + " candidates.", response));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(new ApiResponse<>(false,
                    "Job match failed: " + ex.getMessage(), null));
        }
    }

    private ResponseEntity<ApiResponse<JobMatchResponse>> badRequest(String message) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(false, message, null));
    }

    // ── Saved Candidates ──

    @PostMapping("/candidates/save")
    public ResponseEntity<ApiResponse<SavedCandidate>> saveCandidate(
            Authentication auth,
            @RequestBody Map<String, Object> body) {

        User recruiter = getRecruiter(auth);
        String username = (String) body.get("username");

        if (savedCandidateRepository.existsByRecruiterAndCandidateUsername(recruiter, username)) {
            return ResponseEntity.ok(new ApiResponse<>(true, "Candidate already saved", null));
        }

        SavedCandidate candidate = new SavedCandidate();
        candidate.setRecruiter(recruiter);
        candidate.setCandidateUsername(username);
        candidate.setCandidateName((String) body.get("name"));
        candidate.setCandidateAvatarUrl((String) body.get("avatarUrl"));
        if (body.get("githubId") != null) {
            candidate.setCandidateGithubId(Long.valueOf(body.get("githubId").toString()));
        }
        if (body.get("score") != null) {
            candidate.setCandidateScore(Integer.valueOf(body.get("score").toString()));
        }
        candidate.setCandidateLevel((String) body.get("level"));
        candidate.setCandidateLanguages((String) body.get("languages"));
        candidate.setBookmarked(true);

        SavedCandidate saved = savedCandidateRepository.save(candidate);
        return ResponseEntity.ok(new ApiResponse<>(true, "Candidate saved successfully", saved));
    }

    @DeleteMapping("/candidates/{username}")
    public ResponseEntity<ApiResponse<Void>> unsaveCandidate(
            Authentication auth,
            @PathVariable String username) {

        User recruiter = getRecruiter(auth);
        savedCandidateRepository.findByRecruiterAndCandidateUsername(recruiter, username)
                .ifPresent(savedCandidateRepository::delete);
        return ResponseEntity.ok(new ApiResponse<>(true, "Candidate removed", null));
    }

    @GetMapping("/candidates")
    public ResponseEntity<ApiResponse<List<SavedCandidate>>> listSavedCandidates(Authentication auth) {
        User recruiter = getRecruiter(auth);
        List<SavedCandidate> candidates = savedCandidateRepository.findByRecruiterOrderByCreatedAtDesc(recruiter);
        return ResponseEntity.ok(new ApiResponse<>(true, "Saved candidates fetched", candidates));
    }

    @GetMapping("/candidates/bookmarked")
    public ResponseEntity<ApiResponse<List<SavedCandidate>>> listBookmarkedCandidates(Authentication auth) {
        User recruiter = getRecruiter(auth);
        List<SavedCandidate> candidates = savedCandidateRepository.findByRecruiterAndBookmarkedTrueOrderByCreatedAtDesc(recruiter);
        return ResponseEntity.ok(new ApiResponse<>(true, "Bookmarked candidates fetched", candidates));
    }

    @PutMapping("/candidates/{username}/bookmark")
    public ResponseEntity<ApiResponse<SavedCandidate>> toggleBookmark(
            Authentication auth,
            @PathVariable String username,
            @RequestBody Map<String, Boolean> body) {

        User recruiter = getRecruiter(auth);
        SavedCandidate candidate = savedCandidateRepository
                .findByRecruiterAndCandidateUsername(recruiter, username)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));

        candidate.setBookmarked(body.getOrDefault("bookmarked", !candidate.isBookmarked()));
        candidate.setUpdatedAt(LocalDateTime.now());
        SavedCandidate updated = savedCandidateRepository.save(candidate);
        return ResponseEntity.ok(new ApiResponse<>(true, "Bookmark updated", updated));
    }

    // ── Notes ──

    @PostMapping("/candidates/{username}/notes")
    public ResponseEntity<ApiResponse<RecruiterNote>> addNote(
            Authentication auth,
            @PathVariable String username,
            @RequestBody Map<String, String> body) {

        User recruiter = getRecruiter(auth);
        RecruiterNote note = new RecruiterNote();
        note.setRecruiter(recruiter);
        note.setCandidateUsername(username);
        note.setTitle(body.get("title"));
        note.setContent(body.get("content"));

        RecruiterNote saved = recruiterNoteRepository.save(note);
        return ResponseEntity.ok(new ApiResponse<>(true, "Note added", saved));
    }

    @GetMapping("/candidates/{username}/notes")
    public ResponseEntity<ApiResponse<List<RecruiterNote>>> getNotes(
            Authentication auth,
            @PathVariable String username) {

        User recruiter = getRecruiter(auth);
        List<RecruiterNote> notes = recruiterNoteRepository
                .findByRecruiterAndCandidateUsernameOrderByCreatedAtDesc(recruiter, username);
        return ResponseEntity.ok(new ApiResponse<>(true, "Notes fetched", notes));
    }

    @DeleteMapping("/notes/{noteId}")
    public ResponseEntity<ApiResponse<Void>> deleteNote(
            Authentication auth,
            @PathVariable Long noteId) {

        User recruiter = getRecruiter(auth);
        RecruiterNote note = recruiterNoteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note not found"));
        if (!note.getRecruiter().getId().equals(recruiter.getId())) {
            return ResponseEntity.status(403)
                    .body(new ApiResponse<>(false, "Not authorized", null));
        }
        recruiterNoteRepository.delete(note);
        return ResponseEntity.ok(new ApiResponse<>(true, "Note deleted", null));
    }

    // ── Dashboard stats ──

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats(Authentication auth) {
        User recruiter = getRecruiter(auth);
        long savedCount = savedCandidateRepository.countByRecruiter(recruiter);
        long notedCount = recruiterNoteRepository.findByRecruiterOrderByCreatedAtDesc(recruiter).size();
        return ResponseEntity.ok(new ApiResponse<>(true, "Stats fetched",
                Map.of("savedCandidates", savedCount, "totalNotes", notedCount)));
    }
}
