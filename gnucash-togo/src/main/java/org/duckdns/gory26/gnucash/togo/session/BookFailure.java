package org.duckdns.gory26.gnucash.togo.session;

import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;

/** Why a session's pool could not talk to the book, read from an exception's cause chain. */
public enum BookFailure {

    /** SQLSTATE class 28: Postgres refused the user or password, e.g. after a password change. */
    LOGIN_REFUSED,

    /**
     * No connection: Hikari timed out waiting for one ({@link SQLTransientConnectionException}), the connection
     * broke (SQLSTATE class 08) or the server shut it down (57P0x, as on a restart).
     */
    UNREACHABLE;

    /** The failure behind {@code e}, or null when the book answered and the error is something else. */
    public static @Nullable BookFailure of(Throwable e) {
        BookFailure found = null;
        for(Throwable t = e; t != null; t = t.getCause()) {
            String state = t instanceof SQLException sql && sql.getSQLState() != null ? sql.getSQLState() : "";
            if(state.startsWith("28")) {
                return LOGIN_REFUSED;
            }
            if(t instanceof SQLTransientConnectionException || state.startsWith("08") || state.startsWith("57P0")) {
                found = UNREACHABLE;
            }
        }
        return found;
    }
}
