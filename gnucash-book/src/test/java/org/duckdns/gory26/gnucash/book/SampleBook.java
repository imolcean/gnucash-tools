package org.duckdns.gory26.gnucash.book;

import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code sample-book.sql} (written by GnuCash 5.16 on PG 17.11) in one shared container. psql in the image's
 * init hook loads it, since JDBC can't run {@code COPY … FROM stdin} or {@code \restrict}. Each test gets its
 * own copy of that database through {@code CREATE DATABASE … TEMPLATE}, so writes never leak between tests.
 * Needs only the Postgres driver and Testcontainers next to gnucash-book, no Spring.
 */
@SuppressWarnings("SqlSourceToSinkFlow")
public final class SampleBook {

    // account GUIDs from the dump
    public static final String ROOT = "738965f089a042abb6859881db910878";
    public static final String ASSETS = "1a2898f703dc46a9a6bd160bd20f4ce3";
    public static final String CASH = "c484ef2fae214937865e1dbecc3bba0f";
    public static final String CHECKING = "f98d0fcc0285458cb20c11240b47f75a";
    public static final String CASH_USD = "90678c06f60b44caa83ac70c2b49a0c0";
    public static final String CREDIT_CARD = "bfb633fa8b564e309429cf082efa5e06";
    public static final String INCOME = "d26dc74527ee4236af2851a31f1e5d95";
    public static final String SALARY = "3e761d35833d4eca85f77ff8d8757fc3";
    public static final String EXPENSES = "23e4811038d34013ba9be87db17ec3b5";
    public static final String FOOD = "9297fd595c234e5981140cb09ef4c7ac";
    public static final String GROCERIES = "0e0675c2c42b4c19bffa999c4932f3a8";
    public static final String RENT = "2122c37fb8ef4129978b6a8aa8cacc41";
    public static final String MISC = "2be53f0031e0408cafd491c44cdefac5";
    public static final String OPENING_BALANCES = "bf6d4c349d4a4e51813079c9d16ee76d";
    public static final String HIDDEN = "588435c1276b403eb27a505a681ed33d";
    public static final String HIDDEN_NESTED = "92890de0b32c40f5a67afe144d6087f3";
    public static final String VISIBLE_NESTED = "0f0bd18f3f38458d8059b3099d68fb9f";
    /** {@code Trading}, the placeholder at the top of the trading accounts. */
    public static final String TRADING = "bf28af07cd8a4735890d7370ea09b917";
    /** {@code Trading:CURRENCY}, the placeholder above one trading account per currency. */
    public static final String TRADING_CURRENCY = "4dea01cf6e3d4418b3d8ebac98d111ed";
    public static final String TRADING_EUR = "25d4d674e04b49a798ab2962e86450b5";
    public static final String TRADING_USD = "b6cee3641d1740e3925ffeb28ac37637";

    // commodity GUIDs from the dump; EUR is the currency of every transaction in it
    public static final String EUR = "7c8bcccddbb24b44a3418ca6dbf87106";
    public static final String USD = "1943723210fa4df184b6237b15fabe9a";

    /** Midday on the day the dump was taken, in the user's zone. */
    public static final Clock TODAY = Clock.fixed(Instant.parse("2026-11-04T11:00:00Z"), ZoneId.of("Europe/Berlin"));

    private static final String TEMPLATE = "gnucash";
    private static final String USER = "postgres";
    private static final String PASSWORD = "postgres";

    // The dump's objects are owned by "postgres", so the container's superuser must have that name.
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11")
            .withDatabaseName(TEMPLATE)
            .withUsername(USER)
            .withPassword(PASSWORD)
            .withCopyFileToContainer(MountableFile.forClasspathResource("fixtures/sample-book.sql"),
                    "/docker-entrypoint-initdb.d/sample-book.sql");

    private static final AtomicInteger COPIES = new AtomicInteger();

    static {
        POSTGRES.start(); // stopped by Testcontainers' reaper when the JVM exits
    }

    private SampleBook() {
    }

    /** A private copy of the sample book, connected as the superuser. */
    public static DataSource freshCopy() {
        String name = "book_" + COPIES.incrementAndGet();
        createDatabase(name);
        return dataSource(name, USER, PASSWORD);
    }

    /** A new connection per call, no pool. */
    public static DataSource dataSource(String database, String user, String password) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(url(database));
        dataSource.setUser(user);
        dataSource.setPassword(password);
        return dataSource;
    }

    /** The database name behind a {@link #freshCopy()} data source. */
    public static String databaseOf(DataSource dataSource) {
        return Jdbc.over(dataSource).single("SELECT current_database()", rs -> rs.getString(1));
    }

    /** The book on {@code dataSource} after deleting desktop's lock row, so writes go through. */
    @SuppressWarnings("SqlWithoutWhere") // every lock row goes
    public static GnuCashBook unlocked(DataSource dataSource) {
        Jdbc.over(dataSource).update("DELETE FROM gnclock");
        return new GnuCashBook(dataSource, TODAY);
    }

    /**
     * Replaces {@code database} with a fresh copy of the sample book, ending any sessions still connected to it.
     * For callers that need a fixed database name, such as an app configured once per test context.
     */
    public static void recreate(String database) {
        try(Connection c = DriverManager.getConnection(url("postgres"), USER, PASSWORD);
            Statement statement = c.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
        }
        catch(SQLException e) {
            throw new IllegalStateException("cannot drop " + database, e);
        }
        createDatabase(database);
    }

    /**
     * Gives {@code role} (created when missing, its password set to {@code password}) the ADR-0006 grants on
     * {@code database}: everything the app needs and nothing more. The compose {@code db-init} (M5) grants the same.
     */
    public static void grantAppRole(String database, String role, String password) {
        Jdbc cluster = Jdbc.over(dataSource("postgres", USER, PASSWORD));
        boolean exists = cluster.single("SELECT COUNT(*) FROM pg_roles WHERE rolname = ?", rs -> rs.getInt(1), role) > 0;
        // Roles belong to the whole cluster; a test may have changed the password.
        cluster.update((exists ? "ALTER" : "CREATE") + " ROLE " + role + " LOGIN PASSWORD '" + password + "'");
        Jdbc book = Jdbc.over(dataSource(database, USER, PASSWORD));
        for(String grant : List.of(
                "GRANT CONNECT ON DATABASE " + database + " TO " + role,
                "GRANT USAGE ON SCHEMA public TO " + role,
                "GRANT SELECT ON ALL TABLES IN SCHEMA public TO " + role,
                "GRANT INSERT, UPDATE, DELETE ON transactions, splits, slots TO " + role,
                "GRANT USAGE ON SEQUENCE slots_id_seq TO " + role,
                "GRANT DELETE ON gnclock TO " + role)) {
            book.update(grant);
        }
    }

    public static String host() {
        return POSTGRES.getHost();
    }

    public static int port() {
        return POSTGRES.getMappedPort(5432);
    }

    @SuppressWarnings("BusyWait") // a bounded retry, not a poll
    private static void createDatabase(String name) {
        // The template must have no open sessions; the container's start-up probe may still be closing one.
        for(int attempt = 1; ; attempt++) {
            try(Connection c = DriverManager.getConnection(url("postgres"), USER, PASSWORD);
                Statement statement = c.createStatement()) {
                statement.execute("CREATE DATABASE " + name + " TEMPLATE " + TEMPLATE);
                return;
            }
            catch(SQLException e) {
                if(attempt == 10 || !"55006".equals(e.getSQLState())) {
                    throw new IllegalStateException("cannot copy the sample book", e);
                }
                try {
                    Thread.sleep(200);
                }
                catch(InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ie);
                }
            }
        }
    }

    private static String url(String database) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database;
    }
}
