package com.drtx.bank.shared.audit;

import java.util.List;

import com.drtx.bank.shared.security.CurrentUser;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
class AuditController {

    private final AuditService audit;

    AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    List<AuditService.AuditRecord> list(CurrentUser user, @RequestParam(required = false) String action,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        return audit.page(user.tenantId(), action, Math.max(page, 0), Math.clamp(size, 1, 200));
    }

    @GetMapping("/verify")
    @PreAuthorize("hasAuthority('audit:read')")
    AuditService.Verification verify(CurrentUser user) {
        return audit.verify(user.tenantId());
    }
}
