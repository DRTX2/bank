package com.drtx.bank.identity;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.drtx.bank.shared.security.CurrentUser;
import com.drtx.bank.shared.security.Permission;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RoleController {

    private final RoleService roles;

    RoleController(RoleService roles) {
        this.roles = roles;
    }

    record RoleView(UUID id, String name, String description, boolean system, Set<String> permissions) {
        static RoleView of(Role r) {
            return new RoleView(r.getId(), r.getName(), r.getDescription(), r.isSystem(),
                    new java.util.TreeSet<>(r.getPermissions()));
        }
    }

    record PermissionView(String code, String description, Permission.Scope scope) {
    }

    record CreateRole(@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{2,63}") String name,
                      @Size(max = 255) String description, @NotEmpty Set<String> permissions) {
    }

    record UpdateRole(@Size(max = 255) String description, @NotEmpty Set<String> permissions) {
    }

    @GetMapping("/api/v1/permissions")
    @PreAuthorize("hasAuthority('roles:manage') or hasAuthority('users:read')")
    List<PermissionView> permissions() {
        return Arrays.stream(Permission.values())
                .map(p -> new PermissionView(p.code(), p.description(), p.scope())).toList();
    }

    @GetMapping("/api/v1/roles")
    @PreAuthorize("hasAuthority('roles:manage') or hasAuthority('users:read')")
    List<RoleView> list() {
        return roles.list().stream().map(RoleView::of).toList();
    }

    @PostMapping("/api/v1/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('roles:manage')")
    RoleView create(CurrentUser actor, @Valid @RequestBody CreateRole body) {
        return RoleView.of(roles.create(actor, body.name(), body.description(), body.permissions()));
    }

    @PutMapping("/api/v1/roles/{id}")
    @PreAuthorize("hasAuthority('roles:manage')")
    RoleView update(CurrentUser actor, @PathVariable UUID id, @Valid @RequestBody UpdateRole body) {
        return RoleView.of(roles.update(actor, id, body.description(), body.permissions()));
    }

    @DeleteMapping("/api/v1/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('roles:manage')")
    void delete(CurrentUser actor, @PathVariable UUID id) {
        roles.delete(actor, id);
    }
}
