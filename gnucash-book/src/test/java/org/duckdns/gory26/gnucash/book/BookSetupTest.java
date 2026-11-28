package org.duckdns.gory26.gnucash.book;

import org.duckdns.gory26.gnucash.book.WriteRefusedException.Reason;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADR-0015: accounts, currencies and book options, the rows compared with desktop's as in ADR-0007. */
@Tag("docker")
class BookSetupTest {

    private static final String GOLDEN = "a0000000000000000000000000000001";
    private static final String NEW_TRADING = "a0000000000000000000000000000002";
    private static final String NEW_USD = "a0000000000000000000000000000003";

    private final DataSource db = SampleBook.freshCopy();
    private final GnuCashBook book = SampleBook.unlocked(db);
    private final Sql sql = new Sql(db);

    @Test
    void accountsAreWrittenAsDesktopWroteTheTradingTree() {
        book.createAccount(new NewAccount(GOLDEN, "Golden", SampleBook.ROOT, AccountType.ASSET, SampleBook.EUR, true));
        book.createAccount(new NewAccount(NEW_TRADING, "Trading", GOLDEN, AccountType.TRADING, SampleBook.EUR, true));
        book.createAccount(new NewAccount(NEW_USD, "USD", NEW_TRADING, AccountType.TRADING, SampleBook.USD, false));

        assertThat(account(NEW_TRADING)).isEqualTo(account(SampleBook.TRADING));
        assertThat(slots(NEW_TRADING)).isEqualTo(slots(SampleBook.TRADING)).isNotEmpty();
        assertThat(account(NEW_USD)).isEqualTo(account(SampleBook.TRADING_USD));
        assertThat(slots(NEW_USD)).isEqualTo(slots(SampleBook.TRADING_USD)).isEmpty();
        assertThat(sql.one(String.class, "SELECT parent_guid FROM accounts WHERE guid = ?", NEW_USD)).isEqualTo(NEW_TRADING);
    }

    @Test
    void aNewAccountIsFoundByPathAndTakesSplits() {
        String fuel = "a0000000000000000000000000000004";
        book.createAccount(new NewAccount(fuel, "Fuel", SampleBook.EXPENSES, AccountType.EXPENSE, SampleBook.EUR, false));

        book.insertTransaction(new NewTransaction("b".repeat(32), SampleBook.EUR, LocalDate.of(2026, 11, 3), "Fuel", "",
                Map.of(), List.of(new NewSplit(fuel, new GncNumeric(5000, 100), new GncNumeric(5000, 100)),
                new NewSplit(SampleBook.CASH, new GncNumeric(-5000, 100), new GncNumeric(-5000, 100)))));

        AccountSummary found = book.accountByPath("Expenses:Fuel").orElseThrow();
        assertThat(found.guid()).isEqualTo(fuel);
        assertThat(found.balance()).isEqualTo(new GncNumeric(5000, 100));
        assertThat(AccountTreeTest.find(book.accountTree(), "Expenses:Fuel").guid()).isEqualTo(fuel);
    }

    @Test
    void accountRefusals() {
        assertRefused(() -> book.createAccount(new NewAccount(SampleBook.CASH, "Other", SampleBook.ASSETS, AccountType.CASH,
                SampleBook.EUR, false)), Reason.DUPLICATE_GUID);
        assertThatThrownBy(() -> book.createAccount(new NewAccount(GOLDEN, "Rent", SampleBook.EXPENSES, AccountType.EXPENSE,
                SampleBook.EUR, false)))
                .isInstanceOf(WriteRefusedException.class).hasMessage("Expenses already has an account Rent");
        assertRefused(() -> book.createAccount(new NewAccount(GOLDEN, "X", "0".repeat(32), AccountType.EXPENSE,
                SampleBook.EUR, false)), Reason.ACCOUNT_NOT_ALLOWED);
        assertRefused(() -> book.createAccount(new NewAccount(GOLDEN, "X", "3168b7fe01be4a8ab6cc369b7ff146b9",
                AccountType.EXPENSE, SampleBook.EUR, false)), Reason.ACCOUNT_NOT_ALLOWED);
        assertRefused(() -> book.createAccount(new NewAccount(GOLDEN, "X", SampleBook.EXPENSES, AccountType.EXPENSE,
                "0".repeat(32), false)), Reason.UNKNOWN_COMMODITY);
        assertThatThrownBy(() -> new NewAccount(GOLDEN, "A:B", SampleBook.EXPENSES, AccountType.EXPENSE, SampleBook.EUR, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NewAccount(GOLDEN, "R", SampleBook.EXPENSES, AccountType.ROOT, SampleBook.EUR, false))
                .isInstanceOf(IllegalArgumentException.class);

        GnuCashBook locked = new GnuCashBook(SampleBook.freshCopy(), SampleBook.TODAY);
        assertRefused(() -> locked.createAccount(new NewAccount(GOLDEN, "X", SampleBook.EXPENSES, AccountType.EXPENSE,
                SampleBook.EUR, false)), Reason.LOCKED);
        assertThat(sql.one(Integer.class, "SELECT COUNT(*) FROM accounts")).isEqualTo(24);
    }

    @Test
    void accountsByPathHiddenOnesIncluded() {
        assertThat(book.accountByPath("Expenses:Food:Groceries").map(AccountSummary::guid)).contains(SampleBook.GROCERIES);
        assertThat(book.accountByPath("Hidden:Hidden nested").orElseThrow().hidden()).isTrue();
        assertThat(book.accountByPath("Trading:CURRENCY:USD").map(AccountSummary::guid)).contains(SampleBook.TRADING_USD);
        assertThat(book.accountByPath("Expenses:Nope")).isEmpty();
        assertThat(book.accountByPath("Template Root")).isEmpty();
        assertThat(book.accountByPath("")).isEmpty();
        assertThat(book.rootAccountGuid()).isEqualTo(SampleBook.ROOT);

        sql.update("""
                INSERT INTO accounts VALUES ('cccccccccccccccccccccccccccccccc', 'Rent', 'EXPENSE', ?, 100, 0, ?, '', '', 0, 0)""",
                SampleBook.EUR, SampleBook.EXPENSES);
        assertThatThrownBy(() -> book.accountByPath("Expenses:Rent")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anExistingCurrencyIsFoundNotAdded() {
        assertThat(book.currency("EUR").map(Commodity::guid)).contains(SampleBook.EUR);
        assertThat(book.findOrCreateCurrency("USD").guid()).isEqualTo(SampleBook.USD);
        assertThat(book.currency("CHF")).isEmpty();
        assertThat(sql.one(Integer.class, "SELECT COUNT(*) FROM commodities")).isEqualTo(2);
    }

    @Test
    void aNewCurrencyIsWrittenAsDesktopWroteUsd() {
        Map<String, Object> desktop = commodity(SampleBook.USD);
        // the book without USD: its accounts, transactions and price go too
        sql.update("DELETE FROM prices");
        sql.update("DELETE FROM splits WHERE tx_guid IN (SELECT tx_guid FROM splits s JOIN accounts a ON a.guid = s.account_guid WHERE a.commodity_guid = ?)",
                SampleBook.USD);
        sql.update("DELETE FROM transactions WHERE guid NOT IN (SELECT tx_guid FROM splits)");
        sql.update("DELETE FROM accounts WHERE commodity_guid = ?", SampleBook.USD);
        sql.update("DELETE FROM commodities WHERE guid = ?", SampleBook.USD);

        Commodity usd = book.findOrCreateCurrency("USD");

        assertThat(usd.guid()).matches("[0-9a-f]{32}").isNotEqualTo(SampleBook.USD);
        assertThat(usd).isEqualTo(new Commodity(usd.guid(), "CURRENCY", "USD", 100));
        assertThat(commodity(usd.guid())).isEqualTo(desktop);
        assertThat(book.findOrCreateCurrency("USD")).isEqualTo(usd);
    }

    @Test
    void aCurrencysFractionNameAndCodeComeFromIso4217() {
        Commodity yen = book.findOrCreateCurrency("JPY");

        assertThat(yen.fraction()).isEqualTo(1);
        assertThat(commodity(yen.guid())).containsEntry("fullname", "Japanese Yen").containsEntry("cusip", "392");
        assertThatThrownBy(() -> book.findOrCreateCurrency("XYZ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> book.findOrCreateCurrency("XAU")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bookOptionsFromTheSampleBook() {
        assertThat(book.bookOptions()).isEqualTo(new BookOptions(true, 0));
    }

    @Test
    void bookOptionsAsDesktopStoresOtherValues() {
        sql.update("UPDATE slots SET string_val = 'f' WHERE name = 'options/Accounts/Use Trading Accounts'");
        sql.update("UPDATE slots SET double_val = 30 WHERE name = 'options/Accounts/Day Threshold for Read-Only Transactions (red line)'");

        assertThat(book.bookOptions()).isEqualTo(new BookOptions(false, 30));
    }

    @Test
    void missingBookOptionsAreOff() {
        sql.update("DELETE FROM slots WHERE name LIKE 'options/Accounts/%'");

        assertThat(book.bookOptions()).isEqualTo(new BookOptions(false, 0));
    }

    private Map<String, Object> account(String guid) {
        return sql.row("""
                SELECT name, account_type, commodity_guid, commodity_scu, non_std_scu, code, description, hidden, placeholder
                FROM accounts WHERE guid = ?""", guid);
    }

    private List<Map<String, Object>> slots(String owner) {
        return sql.rows("""
                SELECT name, slot_type, int64_val, string_val, double_val, timespec_val, guid_val, numeric_val_num,
                       numeric_val_denom, gdate_val
                FROM slots WHERE obj_guid = ? ORDER BY name""", owner);
    }

    private Map<String, Object> commodity(String guid) {
        return sql.row("""
                SELECT namespace, mnemonic, fullname, cusip, fraction, quote_flag, quote_source, quote_tz
                FROM commodities WHERE guid = ?""", guid);
    }

    private static void assertRefused(Runnable write, Reason reason) {
        assertThatThrownBy(write::run)
                .isInstanceOf(WriteRefusedException.class)
                .extracting(e -> ((WriteRefusedException) e).reason()).isEqualTo(reason);
    }
}
