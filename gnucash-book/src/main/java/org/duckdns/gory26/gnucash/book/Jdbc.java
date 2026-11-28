package org.duckdns.gory26.gnucash.book;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Plain JDBC with positional {@code ?} parameters and mapped rows (ADR-0014). Over a pool, every call takes a
 * connection and hands it back; inside {@link #inTransaction} every call runs on that transaction's connection.
 * {@link SQLException}s come out as {@link BookAccessException}.
 *
 * <p>Parameters: a {@link Collection} binds as a {@code text[]} for {@code = ANY(?)}, null as SQL NULL, anything else
 * through {@link PreparedStatement#setObject(int, Object)}.
 */
final class Jdbc {

    @FunctionalInterface
    interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private final DataSource dataSource;
    private final Connection connection;

    private Jdbc(DataSource dataSource, Connection connection) {
        this.dataSource = dataSource;
        this.connection = connection;
    }

    static Jdbc over(DataSource dataSource) {
        return new Jdbc(dataSource, null);
    }

    /**
     * Runs {@code work} in one DB transaction on one connection: committed when it returns, rolled back when it
     * throws. The {@link Jdbc} it gets runs every statement on that connection.
     */
    <T> T inTransaction(Function<Jdbc, T> work) {
        if(connection != null) {
            throw new IllegalStateException("already in a transaction");
        }
        try(Connection c = dataSource.getConnection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                T result = work.apply(new Jdbc(null, c));
                c.commit();
                return result;
            }
            catch(RuntimeException | Error e) {
                rollback(c, e);
                throw e;
            }
            catch(SQLException e) {
                rollback(c, e);
                throw e;
            }
            finally {
                c.setAutoCommit(autoCommit);
            }
        }
        catch(SQLException e) {
            throw new BookAccessException("the transaction failed", e);
        }
    }

    <T> List<T> list(String sql, RowMapper<T> mapper, Object... params) {
        return run(sql, params, statement -> {
            List<T> rows = new ArrayList<>();
            try(ResultSet rs = statement.executeQuery()) {
                while(rs.next()) {
                    rows.add(mapper.map(rs));
                }
            }
            return rows;
        });
    }

    /** The only row, or empty when there is none; more than one is a bug in the query. */
    <T> Optional<T> optional(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = list(sql, mapper, params);
        if(rows.size() > 1) {
            throw new IllegalStateException("expected at most one row, got " + rows.size() + ": " + sql);
        }
        return rows.stream().findFirst();
    }

    /** Exactly one row. */
    <T> T single(String sql, RowMapper<T> mapper, Object... params) {
        return optional(sql, mapper, params)
                .orElseThrow(() -> new IllegalStateException("expected one row, got none: " + sql));
    }

    /** @return the number of rows changed */
    int update(String sql, Object... params) {
        return run(sql, params, PreparedStatement::executeUpdate);
    }

    @FunctionalInterface
    private interface Statement<T> {
        T run(PreparedStatement statement) throws SQLException;
    }

    private <T> T run(String sql, Object[] params, Statement<T> body) {
        try {
            if(connection != null) {
                return prepareAndRun(connection, sql, params, body);
            }
            try(Connection c = dataSource.getConnection()) {
                return prepareAndRun(c, sql, params, body);
            }
        }
        catch(SQLException e) {
            throw new BookAccessException("SQL failed: " + sql, e);
        }
    }

    private static <T> T prepareAndRun(Connection c, String sql, Object[] params, Statement<T> body) throws SQLException {
        try(PreparedStatement statement = c.prepareStatement(sql)) {
            for(int i = 0; i < params.length; i++) {
                bind(c, statement, i + 1, params[i]);
            }
            return body.run(statement);
        }
    }

    private static void bind(Connection c, PreparedStatement statement, int index, Object value) throws SQLException {
        switch(value) {
            case null -> statement.setNull(index, Types.NULL);
            case Collection<?> values -> statement.setArray(index, c.createArrayOf("text", values.toArray()));
            default -> statement.setObject(index, value);
        }
    }

    private static void rollback(Connection c, Throwable cause) {
        try {
            c.rollback();
        }
        catch(SQLException e) {
            cause.addSuppressed(e);
        }
    }
}
