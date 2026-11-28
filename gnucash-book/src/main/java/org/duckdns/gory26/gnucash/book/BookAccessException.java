package org.duckdns.gory26.gnucash.book;

import java.sql.SQLException;

/**
 * The book could not be read or written: the database refused, broke the connection or rejected a statement.
 * The {@link SQLException} is the cause, with its SQLSTATE. A write that fails this way changed nothing.
 */
public class BookAccessException extends RuntimeException {

    public BookAccessException(String message, SQLException cause) {
        super(message, cause);
    }

    @Override
    public synchronized SQLException getCause() {
        return (SQLException) super.getCause();
    }
}
