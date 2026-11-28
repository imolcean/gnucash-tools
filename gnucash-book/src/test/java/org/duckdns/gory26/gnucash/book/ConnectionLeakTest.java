package org.duckdns.gory26.gnucash.book;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every call hands its connection back. The app runs the book on a pool of two connections per session, so
 * one leaked connection per page load stalls the third page.
 */
@Tag("docker")
class ConnectionLeakTest {

    /** Counts connections handed out and not yet closed. */
    private static final class CountingDataSource implements DataSource {

        private final DataSource target;
        private final AtomicInteger open = new AtomicInteger();

        CountingDataSource(DataSource target) {
            this.target = target;
        }

        @Override
        public Connection getConnection(String user, String password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return target.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            target.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            target.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return target.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return target.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> type) throws SQLException {
            return target.unwrap(type);
        }

        @Override
        public boolean isWrapperFor(Class<?> type) throws SQLException {
            return target.isWrapperFor(type);
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = target.getConnection();
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
        book.transaction("0".repeat(32));
        book.transactions(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        book.accountByPath("Expenses:Rent");
        book.rootAccountGuid();
        book.currency("EUR");
        book.bookOptions();

        assertThat(db.open()).isZero();
    }

    @Test
    void writesReturnTheirConnections() {
        book.clearLock();
        String tx = book.insert(new TransferDraft(LocalDate.of(2026, 11, 3), List.of(), "x",
                new GncNumeric(100, 100), SampleBook.CASH, SampleBook.RENT, ""));
        book.delete(tx);
        String guid = "1".repeat(32);
        book.insertTransaction(new NewTransaction(guid, SampleBook.EUR, LocalDate.of(2026, 11, 3), "y", "", Map.of(),
                List.of(new NewSplit(SampleBook.CASH, new GncNumeric(-100, 100), new GncNumeric(-100, 100)),
                        new NewSplit(SampleBook.RENT, new GncNumeric(100, 100), new GncNumeric(100, 100)))));
        assertThatThrownBy(() -> book.insertTransaction(new NewTransaction(guid, SampleBook.EUR, LocalDate.of(2026, 11, 3),
                "y", "", Map.of(), List.of(new NewSplit(SampleBook.CASH, new GncNumeric(0, 100), new GncNumeric(0, 100))))))
                .isInstanceOf(WriteRefusedException.class);
        book.createAccount(new NewAccount("2".repeat(32), "New", SampleBook.EXPENSES, AccountType.EXPENSE, SampleBook.EUR, false));
        book.findOrCreateCurrency("CHF");

        assertThat(db.open()).isZero();
    }
}
