package com.drtx.bank.identity;

import com.drtx.bank.shared.security.Permission;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Keeps the permissions table in sync with the {@link Permission} enum (the code is the source of truth). */
@Component
@Order(0)
class PermissionCatalogSync implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    PermissionCatalogSync(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Permission p : Permission.values()) {
            jdbc.update("""
                    insert into permissions (code, description, scope) values (?, ?, ?)
                    on conflict (code) do update set description = excluded.description, scope = excluded.scope""",
                    p.code(), p.description(), p.scope().name());
        }
    }
}
