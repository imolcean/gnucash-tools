package org.duckdns.gory26.gnucash.togo.session;

import com.zaxxer.hikari.HikariDataSource;
import org.duckdns.gory26.gnucash.book.GnuCashBook;

import java.security.Principal;

/**
 * One logged-in user's way into the book: their own small pool, opened with their login (ADR-0006). It is the
 * authentication principal, so it survives Spring Security's session-id change at login; the password lives
 * only inside the pool.
 */
public final class Session implements Principal, AutoCloseable {

    private final String username;
    private final HikariDataSource pool;
    private final GnuCashBook book;

    Session(String username, HikariDataSource pool, GnuCashBook book) {
        this.username = username;
        this.pool = pool;
        this.book = book;
    }

    @Override
    public String getName() {
        return username;
    }

    public GnuCashBook book() {
        return book;
    }

    @Override
    public void close() {
        pool.close();
    }

    @Override
    public String toString() {
        return username;
    }

    /** Hikari's name for this session's pool, as its own log lines show it. */
    String poolName() {
        return pool.getPoolName();
    }
}
