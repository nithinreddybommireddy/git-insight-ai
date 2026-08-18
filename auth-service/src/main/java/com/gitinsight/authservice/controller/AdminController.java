package com.gitinsight.authservice.controller;


import com.gitinsight.authservice.entity.User;
import com.gitinsight.authservice.repository.UserRepository;
import com.gitinsight.common.dto.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<User>>> getUsers() {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Users fetched successfully",
                        userRepository.findAll()
                )
        );
    }

    @PatchMapping("/users/{userId}/role")
    public ResponseEntity<ApiResponse<User>> updateRole(
            @PathVariable Long userId,
            @RequestParam User.Role role) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        User.Role currentRole = user.getRole();

        // Prevent an admin from removing the last ADMIN account.
        if (currentRole == User.Role.ADMIN
                && role != User.Role.ADMIN
                && userRepository.countByRole(User.Role.ADMIN) <= 1) {

            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(
                            false,
                            "Cannot remove the last administrator.",
                            null
                    ));
        }

        user.setRole(role);
        User updatedUser = userRepository.save(user);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "User role updated successfully",
                        updatedUser
                )
        );
    }
}