package org.duckdns.gory26.gnucash.reports;

import org.duckdns.gory26.gnucash.book.SampleBook;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The books the report tests run on, each a private copy of {@link SampleBook} with more rows written to it.
 * The shared dump is never changed: other modules' tests assert its balances.
 * <p>
 * Amounts are cents. Rows have the shape GnuCash 5.16 writes (ADR-0007).
 */
final class Books {

    // accounts of the dump that SampleBook does not name
    static final String INCOME = "d26dc74527ee4236af2851a31f1e5d95";
    static final String SALARY = "3e761d35833d4eca85f77ff8d8757fc3";
    static final String EXPENSES = "23e4811038d34013ba9be87db17ec3b5";

    private final JdbcClient jdbc;
    private final String database;
    private int rows;

    private Books(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
        this.database = SampleBook.databaseOf(dataSource);
    }

    /** The dump as it is: 8 transactions in November 2026, one income and three expense accounts, all through Cash. */
    static String sample() {
        return new Books(SampleBook.freshCopy()).database;
    }

    /**
     * The dump plus what it lacks: a second income account, transactions shared by two selected accounts, an
     * expense account with net refunds, a group of small accounts, and a transfer between selected accounts.
     * <pre>
     * revenue  2,000.00 + bonus 1,000.00 + interest 12.50              = 3,012.50
     * expenses   740.00 + restaurants 60.00 - refunds 15.00 + leisure 16.00 = 801.00
     * </pre>
     */
    static String rich() {
        Books book = new Books(SampleBook.freshCopy());
        String interest = book.account("Interest", "INCOME", INCOME);
        String restaurants = book.account("Restaurants", "EXPENSE", SampleBook.FOOD);
        String refunds = book.account("Refunds", "EXPENSE", SampleBook.FOOD);
        String leisure = book.account("Leisure", "EXPENSE", EXPENSES);
        String books = book.account("Books", "EXPENSE", leisure);
        String cinema = book.account("Cinema", "EXPENSE", leisure);
        String games = book.account("Games", "EXPENSE", leisure);

        LocalDate day = LocalDate.of(2026, 11, 12);
        book.transaction(day, "Interest", leg(SampleBook.CHECKING, 1250), leg(interest, -1250));
        book.transaction(day, "Bonus, part in cash",
                leg(SampleBook.CASH, 60000), leg(SampleBook.CHECKING, 40000), leg(SALARY, -100000));
        book.transaction(day, "Dinner, paid from two pockets",
                leg(restaurants, 6000), leg(SampleBook.CASH, -2000), leg(SampleBook.CHECKING, -4000));
        book.transaction(day, "Deposit returned", leg(SampleBook.CASH, 1500), leg(refunds, -1500));
        book.transaction(day, "Novel", leg(books, 500), leg(SampleBook.CHECKING, -500));
        book.transaction(day, "Matinee", leg(cinema, 800), leg(SampleBook.CHECKING, -800));
        book.transaction(day, "Dice", leg(games, 300), leg(SampleBook.CHECKING, -300));
        book.transaction(day, "To the bank", leg(SampleBook.CHECKING, 5000), leg(SampleBook.CASH, -5000));
        return book.database;
    }

    /** The dump plus one expense larger than the income: revenue 2,000.00, expenses 5,740.00. */
    static String loss() {
        Books book = new Books(SampleBook.freshCopy());
        book.transaction(LocalDate.of(2026, 11, 12), "A year's rent in advance",
                leg(SampleBook.RENT, 500000), leg(SampleBook.CHECKING, -500000));
        return book.database;
    }

    private record Leg(String account, long cents) {
    }

    private static Leg leg(String account, long cents) {
        return new Leg(account, cents);
    }

    private String account(String name, String type, String parent) {
        String guid = guid();
        jdbc.sql("""
                        INSERT INTO accounts (guid, name, account_type, commodity_guid, commodity_scu, non_std_scu,
                                              parent_guid, code, description, hidden, placeholder)
                        VALUES (:guid, :name, :type, :eur, 100, 0, :parent, '', '', 0, 0)""")
                .param("guid", guid).param("name", name).param("type", type)
                .param("eur", SampleBook.EUR).param("parent", parent)
                .update();
        return guid;
    }

    private void transaction(LocalDate date, String description, Leg... legs) {
        long sum = 0;
        for(Leg leg : legs) {
            sum += leg.cents();
        }
        if(sum != 0) {
            throw new IllegalArgumentException(description + " does not balance: " + sum);
        }
        String guid = guid();
        jdbc.sql("""
                        INSERT INTO transactions (guid, currency_guid, num, post_date, enter_date, description)
                        VALUES (:guid, :eur, '', CAST(:date AS date) + time '10:59:00', CAST(:date AS date) + time '12:00:00', :description)""")
                .param("guid", guid).param("eur", SampleBook.EUR).param("date", date).param("description", description)
                .update();
        jdbc.sql("""
                        INSERT INTO slots (obj_guid, name, slot_type, int64_val, timespec_val,
                                           numeric_val_num, numeric_val_denom, gdate_val)
                        VALUES (:guid, 'date-posted', 10, 0, '1970-01-01 00:00:00', 0, 1, :date)""")
                .param("guid", guid).param("date", date)
                .update();
        for(Leg leg : legs) {
            jdbc.sql("""
                            INSERT INTO splits (guid, tx_guid, account_guid, memo, action, reconcile_state,
                                                reconcile_date, value_num, value_denom, quantity_num, quantity_denom)
                            VALUES (:guid, :tx, :account, '', '', 'n', '1970-01-01 00:00:00', :cents, 100, :cents, 100)""")
                    .param("guid", guid()).param("tx", guid).param("account", leg.account()).param("cents", leg.cents())
                    .update();
        }
    }

    /** Not random: the order GnuCash returns rows in can depend on their GUIDs, and the baselines are exact. */
    private String guid() {
        byte[] name = ("gnucash-reports test row " + rows++).getBytes(StandardCharsets.UTF_8);
        return UUID.nameUUIDFromBytes(name).toString().replace("-", "");
    }
}
