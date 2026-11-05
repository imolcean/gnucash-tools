package org.duckdns.gory26.gnucash.book;

import org.postgresql.Driver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code sample-book.sql} (written by GnuCash 5.16 on PG 17.11) in one shared container. psql in the image's
 * init hook loads it, since JDBC can't run {@code COPY … FROM stdin} or {@code \restrict}. Each test gets its
 * own copy of that database through {@code CREATE DATABASE … TEMPLATE}, so writes never leak between tests.
 */
@SuppressWarnings("SqlSourceToSinkFlow")
public final class SampleBook {

    // account GUIDs from the dump
    public static final String CASH = "c484ef2fae214937865e1dbecc3bba0f";
    public static final String CHECKING = "f98d0fcc0285458cb20c11240b47f75a";
    public static final String CASH_USD = "90678c06f60b44caa83ac70c2b49a0c0";
    public static final String FOOD = "9297fd595c234e5981140cb09ef4c7ac";
    public static final String GROCERIES = "0e0675c2c42b4c19bffa999c4932f3a8";
    public static final String RENT = "2122c37fb8ef4129978b6a8aa8cacc41";
    public static final String MISC = "2be53f0031e0408cafd491c44cdefac5";
    public static final String HIDDEN = "588435c1276b403eb27a505a681ed33d";
    public static final String VISIBLE_NESTED = "0f0bd18f3f38458d8059b3099d68fb9f";
    public static final String TRADING_EUR = "25d4d674e04b49a798ab2962e86450b5";
    public static final String TRADING_USD = "b6cee3641d1740e3925ffeb28ac37637";
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

    public static DataSource dataSource(String database, String user, String password) {
        return new SimpleDriverDataSource(new Driver(), url(database), user, password);
    }

    /** The database name behind a {@link #freshCopy()} data source. */
    public static String databaseOf(DataSource dataSource) {
        return JdbcClient.create(dataSource).sql("SELECT current_database()").query(String.class).single();
    }

    @SuppressWarnings("SqlWithoutWhere") // every lock row goes
    public static GnuCashBook unlocked(DataSource dataSource) {
        JdbcClient.create(dataSource).sql("DELETE FROM gnclock").update();
        return new GnuCashBook(dataSource, TODAY);
    }

    /**
     * Replaces {@code database} with a fresh copy of the sample book, ending any sessions still connected to it.
     * For callers that need a fixed database name, such as an app configured once per test context.
     */
    public static void recreate(String database) {
        try (Connection c = DriverManager.getConnection(url("postgres"), USER, PASSWORD)) {
            c.createStatement().execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
        } catch (SQLException e) {
            throw new IllegalStateException("cannot drop " + database, e);
        }
        createDatabase(database);
    }

    /**
     * Gives {@code role} (created when missing, its password set to {@code password}) the ADR-0006 grants on
     * {@code database}: everything the app needs and nothing more. The compose {@code db-init} (M5) grants the same.
     */
    public static void grantAppRole(String database, String role, String password) {
        JdbcClient cluster = JdbcClient.create(dataSource("postgres", USER, PASSWORD));
        if (cluster.sql("SELECT COUNT(*) FROM pg_roles WHERE rolname = :role").param("role", role)
                .query(Integer.class).single() == 0) {
            cluster.sql("CREATE ROLE " + role + " LOGIN PASSWORD '" + password + "'").update();
        } else {
            // Roles belong to the whole cluster; a test may have changed the password.
            cluster.sql("ALTER ROLE " + role + " LOGIN PASSWORD '" + password + "'").update();
        }
        JdbcClient book = JdbcClient.create(dataSource(database, USER, PASSWORD));
        for (String grant : List.of(
                "GRANT CONNECT ON DATABASE " + database + " TO " + role,
                "GRANT USAGE ON SCHEMA public TO " + role,
                "GRANT SELECT ON ALL TABLES IN SCHEMA public TO " + role,
                "GRANT INSERT, UPDATE, DELETE ON transactions, splits, slots TO " + role,
                "GRANT USAGE ON SEQUENCE slots_id_seq TO " + role,
                "GRANT DELETE ON gnclock TO " + role)) {
            book.sql(grant).update();
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
        for (int attempt = 1; ; attempt++) {
            try (Connection c = DriverManager.getConnection(url("postgres"), USER, PASSWORD)) {
                c.createStatement().execute("CREATE DATABASE " + name + " TEMPLATE " + TEMPLATE);
                return;
            } catch (SQLException e) {
                if (attempt == 10 || !"55006".equals(e.getSQLState())) {
                    throw new IllegalStateException("cannot copy the sample book", e);
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
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
