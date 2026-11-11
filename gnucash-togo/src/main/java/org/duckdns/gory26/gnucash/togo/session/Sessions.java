package org.duckdns.gory26.gnucash.togo.session;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.duckdns.gory26.gnucash.book.GnuCashBook;
import org.duckdns.gory26.gnucash.togo.config.TogoDbProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Every open {@link Session}, so that each pool is closed exactly once: on logout, when its HTTP session
 * is destroyed, or when the app stops.
 */
@Component
public class Sessions {

    /** Two connections per phone session are plenty (ADR-0006). */
    private static final int POOL_SIZE = 2;

    /** Phone use comes in bursts; between them a session holds no connection. */
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(1);

    private final TogoDbProperties db;
    private final Clock clock;
    private final Set<Session> open = ConcurrentHashMap.newKeySet();
    private final AtomicInteger opened = new AtomicInteger();

    public Sessions(TogoDbProperties db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public int openCount() {
        return open.size();
    }

    @PreDestroy
    public void closeAll() {
        open.forEach(this::close);
    }

    /**
     * Opens a pool with the login's credentials and proves it with one connection.
     *
     * @throws com.zaxxer.hikari.pool.HikariPool.PoolInitializationException if the database refuses the login
     *                                                                       or cannot be reached; nothing stays open
     */
    Session open(String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("book-" + opened.incrementAndGet());
        config.setJdbcUrl(db.jdbcUrl());
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(POOL_SIZE);
        // Sessions never expire, so idle connections should: none are kept between bursts of requests.
        config.setMinimumIdle(0);
        config.setIdleTimeout(IDLE_TIMEOUT.toMillis());
        // A book that is down fails the request soon (UnreachableBookAdvice) instead of after Hikari's 30 s.
        // Hikari checks a connection idle for over 500 ms before handing it out, so after a database restart
        // the next request reconnects with the stored login and nobody has to log in again.
        config.setConnectionTimeout(db.connectionTimeout().toMillis());
        // Connect once while the pool is built, so a wrong password fails the login right here.
        config.setInitializationFailTimeout(1);
        HikariDataSource pool = new HikariDataSource(config);
        Session session = new Session(username, pool, new GnuCashBook(pool, clock));
        open.add(session);
        return session;
    }

    void close(Session session) {
        open.remove(session);
        session.close();
    }
}
