package org.duckdns.gory26.gnucash.book;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("docker")
class SchemaAndLockTest {

    private final DataSource db = SampleBook.freshCopy();
    private final GnuCashBook book = new GnuCashBook(db, SampleBook.TODAY);
    private final JdbcClient sql = JdbcClient.create(db);

    @Test
    void theSampleBooksVersionsAreTheKnownSet() {
        assertThat(book.schemaStatus().known()).isTrue();
        assertThat(GnuCashBook.KNOWN_VERSIONS).hasSize(22);
    }

    @Test
    void theApplicationVersionRowsAreIgnored() {
        sql.sql("UPDATE versions SET table_version = 5000099 WHERE table_name = 'Gnucash'").update();
        sql.sql("UPDATE versions SET table_version = 1 WHERE table_name = 'Gnucash-Resave'").update();

        assertThat(book.schemaStatus().known()).isTrue();
    }

    @Test
    void aChangedMissingOrExtraTableMakesTheSchemaUnknown() {
        sql.sql("UPDATE versions SET table_version = 6 WHERE table_name = 'splits'").update();
        sql.sql("DELETE FROM versions WHERE table_name = 'lots'").update();
        sql.sql("INSERT INTO versions VALUES ('wormholes', 1)").update();

        SchemaStatus status = book.schemaStatus();

        assertThat(status.known()).isFalse();
        assertThat(status.differences()).containsExactly(
                "lots: missing, expected version 2",
                "splits: version 6, expected 5",
                "wormholes: unexpected table, version 1");
    }

    @Test
    void theDumpIsStillLockedByTheDesktopThatWroteIt() {
        assertThat(book.lock()).contains(new LockHolder("Leopard", 8300));
    }

    @Test
    void clearingTheLockRemovesIt() {
        book.clearLock();

        assertThat(book.lock()).isEmpty();
    }
}
