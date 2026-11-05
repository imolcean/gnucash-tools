package org.duckdns.gory26.gnucash.book;

/** The desktop session holding the book, from its {@code gnclock} row (ADR-0004). */
public record LockHolder(String hostname, int pid) {
}
