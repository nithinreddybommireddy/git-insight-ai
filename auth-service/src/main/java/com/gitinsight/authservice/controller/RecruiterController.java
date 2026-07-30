package com.gitinsight.authservice.controller;

import com.gitinsight.authservice.entity.RecruiterNote;
import com.gitinsight.authservice.entity.SavedCandidate;
import com.gitinsight.authservice.entity.User;
import com.gitinsight.authservice.repository.RecruiterNoteRepository;
import com.gitinsight.authservice.repository.SavedCandidateRepository;
import com.gitinsight.authservice.repository.UserRepository;
import com.gitinsight.common.dto.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recruiter")
@PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
public class RecruiterController {

    private final SavedCandidateRepository savedCandidateRepository;
    private final RecruiterNoteRepository recruiterNoteRepository;
    private final UserRepository userRepository;

    public RecruiterController(SavedCandidateRepository savedCandidateRepository,
                                RecruiterNoteRepository recruiterNoteRepository,
                                UserRepository userRepository) {
        this.savedCandidateRepository = savedCandidateRepository;
        this.recruiterNoteRepository = recruiterNoteRepository;
        this.userRepository = userRepository;
    }

    private User getRecruiter(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Recruiter not found"));
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
