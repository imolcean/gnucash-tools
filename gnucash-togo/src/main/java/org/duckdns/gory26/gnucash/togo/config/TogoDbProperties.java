package org.duckdns.gory26.gnucash.togo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Where the GnuCash book lives ({@code GNUCASH_DB_HOST}, {@code GNUCASH_DB_PORT}, {@code GNUCASH_DB_NAME}).
 * The user and password come from the login (ADR-0006), so nothing here connects at startup.
 *
 * @param connectionTimeout how long a request waits for a connection before the page says the book is
 *                          unreachable
 */
@ConfigurationProperties("togo.db")
public record TogoDbProperties(String host, @DefaultValue("5432") int port, String name,
                               @DefaultValue("5s") Duration connectionTimeout) {

    public String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + name;
    }

    /** False until host and database name are set; login then fails with a clear message. */
    public boolean configured() {
        return host != null && !host.isBlank() && name != null && !name.isBlank();
    }
}
