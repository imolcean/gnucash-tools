package org.duckdns.gory26.gnucash.book;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every call hands its connection back. The app runs the book on a pool of two connections per session, so
 * one leaked connection per page load stalls the third page.
 */
class ConnectionLeakTest {

    /** Counts connections handed out and not yet closed. */
    @NullMarked
    private static final class CountingDataSource extends DelegatingDataSource {

        private final AtomicInteger open = new AtomicInteger();

        CountingDataSource(DataSource target) {
            super(target);
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = super.getConnection();
            open.incrementAndGet();
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (_, method, args) -> {
                        if(method.getName().equals("close") && !connection.isClosed()) {
                            open.decrementAndGet();
                        }
                        try {
                            return method.invoke(connection, args);
                        }
                        catch(InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }

        int open() {
            return open.get();
        }
    }
    private final CountingDataSource db = new CountingDataSource(SampleBook.freshCopy());
    private final GnuCashBook book = new GnuCashBook(db, SampleBook.TODAY);

    @Test
    void readsReturnTheirConnections() {
        book.schemaStatus();
        book.lock();
        book.accountTree();
        book.account(SampleBook.CASH);
        book.register(SampleBook.CASH, 2);
        book.suggestDescriptions("Re");
        book.quickFill("Rewe");
        book.suggestTags("");
        book.suggestAccounts("");
        book.draft("0".repeat(32));

        assertThat(db.open()).isZero();
    }

    @Test
    void writesReturnTheirConnections() {
        book.clearLock();
        String tx = book.insert(new TransferDraft(LocalDate.of(2026, 11, 3), List.of(), "x",
                new GncNumeric(100, 100), SampleBook.CASH, SampleBook.RENT, ""));
        book.delete(tx);

        assertThat(db.open()).isZero();
    }
}
