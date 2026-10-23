package com.drtx.bank.shared.tenancy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

/**
 * Hands the tenant Hibernate filters on to PostgreSQL: row-level security reads {@code app.tenant_id}, which is local
 * to the transaction, so it can never leak to the next user of a pooled connection (ADR-0009).
 */
@Component
class TenantRlsListener implements TransactionExecutionListener {

    private final ObjectProvider<DataSource> dataSource;

    TenantRlsListener(ObjectProvider<DataSource> dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterBegin(TransactionExecution transaction, Throwable failure) {
        if (failure != null) {
            return;
        }
        DataSource ds = dataSource.getObject();
        Connection connection = DataSourceUtils.getConnection(ds);
        try (var statement = connection.prepareStatement("select set_config('app.tenant_id', ?, true)")) {
            statement.setString(1, setting(TenantContext.current()));
            statement.execute();
        } catch (SQLException e) {
            throw new CannotCreateTransactionException("Cannot bind the tenant to the transaction", e);
        } finally {
            DataSourceUtils.releaseConnection(connection, ds);
        }
    }

    static String setting(UUID tenant) {
        if (TenantContext.SYSTEM.equals(tenant)) {
            return "system";
        }
        return TenantContext.NONE.equals(tenant) ? "" : tenant.toString();
    }
}
