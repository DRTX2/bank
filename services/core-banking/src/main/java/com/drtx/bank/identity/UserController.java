package com.drtx.bank.identity;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.drtx.bank.shared.security.CurrentUser;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
class UserController {

    private final UserService users;

    UserController(UserService users) {
        this.users = users;
    }

    record UserView(UUID id, String email, String fullName, String nationalId, User.Status status, boolean locked,
                    Set<String> roles, Instant createdAt) {
        static UserView of(User u) {
            return new UserView(u.getId(), u.getEmail(), u.getFullName(), u.getNationalId(), u.getStatus(),
                    u.isLocked(Instant.now()), u.roleNames(), u.getCreatedAt());
        }
    }

    record CreateUser(@NotBlank @Email String email, @NotBlank @Size(max = 120) String fullName,
                      @Size(max = 20) String nationalId, @NotBlank String password, @NotEmpty Set<String> roles) {
    }

    record RoleAssignment(@NotEmpty Set<String> roles) {
    }

    record StatusChange(@NotNull User.Status status) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('users:read')")
    List<UserView> list() {
        return users.list().stream().map(UserView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('users:manage')")
    UserView create(CurrentUser actor, @Valid @RequestBody CreateUser body) {
        return UserView.of(users.create(actor, body.email(), body.fullName(), body.nationalId(), body.password(),
                body.roles()));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('users:manage')")
    UserView assignRoles(CurrentUser actor, @PathVariable UUID id, @Valid @RequestBody RoleAssignment body) {
        return UserView.of(users.assignRoles(actor, id, body.roles()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('users:manage')")
    UserView changeStatus(CurrentUser actor, @PathVariable UUID id, @Valid @RequestBody StatusChange body) {
        return UserView.of(users.changeStatus(actor, id, body.status()));
    }
}
