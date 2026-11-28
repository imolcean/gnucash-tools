package org.duckdns.gory26.gnucash.book;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("docker")
class AccountTreeTest {

    private static final LocalDate DUMP_DAY = LocalDate.of(2026, 11, 4);

    private final DataSource db = SampleBook.freshCopy();
    private final GnuCashBook book = new GnuCashBook(db, SampleBook.TODAY);
    private final Sql sql = new Sql(db);

    private static BigDecimal total(List<AccountNode> tree, String path) {
        return find(tree, path).displayTotal().map(GncNumeric::toBigDecimal).orElseThrow();
    }

    static AccountNode find(List<AccountNode> tree, String path) {
        Optional<AccountNode> node = flatten(tree).filter(n -> n.path().equals(path)).findFirst();
        assertThat(node).as("account %s in the tree", path).isPresent();
        return node.get();
    }

    static Stream<AccountNode> flatten(List<AccountNode> nodes) {
        return nodes.stream().flatMap(n -> Stream.concat(Stream.of(n), flatten(n.children())));
    }

    /** The plan's expected Present balances on 2026-11-04, as desktop shows them. */
    @ParameterizedTest
    @CsvSource({
            "Assets:Cash,                 1585.00",
            "Assets:Cash USD,             0.00",
            "Expenses:Food:Groceries,     20.00",
            "Expenses:Rent,               520.00",
            "Expenses:Misc,               0.00",       // the 200.00 is dated 2026-11-10
            "Income:Salary,               2000.00",    // sign reversed
            "Equity:Opening Balances,     100.00",     // sign reversed
            "Trading:CURRENCY:EUR,        -25.00",     // desktop does not reverse TRADING
            "Trading:CURRENCY:USD,        0.00",
    })
    void presentBalancesMatchDesktop(String path, BigDecimal expected) {
        AccountNode account = find(book.accountTree(DUMP_DAY), path);

        assertThat(account.displayBalance().toBigDecimal()).isEqualTo(expected);
        assertThat(account.displayTotal().orElseThrow().toBigDecimal()).isEqualTo(expected);
    }

    @Test
    void oneAccountCarriesItsPathTypeCommodityAndPresentBalance() {
        AccountSummary cash = book.account(SampleBook.CASH).orElseThrow();

        assertThat(cash.path()).isEqualTo("Assets:Cash");
        assertThat(cash.name()).isEqualTo("Cash");
        assertThat(cash.type()).isEqualTo(AccountType.CASH);
        assertThat(cash.commodity().mnemonic()).isEqualTo("EUR");
        assertThat(cash.commodity().fraction()).isEqualTo(100);
        assertThat(cash.placeholder()).isFalse();
        assertThat(cash.hidden()).isFalse();
        assertThat(cash.displayBalance().toBigDecimal()).isEqualTo("1585.00"); // not the future concert
    }

    @Test
    void oneAccountShowsItsBalanceWithDesktopsSign() {
        assertThat(book.account(find(book.accountTree(DUMP_DAY), "Income:Salary").guid()).orElseThrow()
                .displayBalance().toBigDecimal()).isEqualTo("2000.00");
    }

    @Test
    void oneAccountTellsPlaceholdersAndHiddenOnesApart() {
        assertThat(book.account(SampleBook.FOOD).orElseThrow().placeholder()).isTrue();
        assertThat(book.account(SampleBook.VISIBLE_NESTED).orElseThrow().hidden()).isTrue(); // through its parent
    }

    @Test
    void anUnknownAccountIsEmpty() {
        assertThat(book.account("0".repeat(32))).isEmpty();
    }

    @Test
    void parentsRollUpTheirChildren() {
        List<AccountNode> tree = book.accountTree(DUMP_DAY);

        assertThat(total(tree, "Assets")).isEqualTo("1585.00");
        assertThat(total(tree, "Expenses")).isEqualTo("540.00");
        assertThat(total(tree, "Expenses:Food")).isEqualTo("20.00");
        assertThat(total(tree, "Income")).isEqualTo("2000.00");
        assertThat(total(tree, "Trading")).isEqualTo("-25.00");
        assertThat(find(tree, "Assets").displayBalance().toBigDecimal()).isEqualTo("0.00");
    }

    @Test
    void futureDatedEntriesCountOnceTheirDayHasCome() {
        List<AccountNode> tree = book.accountTree(LocalDate.of(2026, 11, 10));

        assertThat(total(tree, "Expenses:Misc")).isEqualTo("200.00");
        assertThat(total(tree, "Assets:Cash")).isEqualTo("1385.00");
    }

    @Test
    void entriesBeforeTheirDayAreNotYetPresent() {
        assertThat(total(book.accountTree(LocalDate.of(2026, 11, 2)), "Assets:Cash")).isEqualTo("80.00");
    }

    @Test
    void hiddenAccountsAndTheirSubtreesAreLeftOut() {
        List<String> paths = flatten(book.accountTree(DUMP_DAY)).map(AccountNode::path).toList();

        assertThat(paths).doesNotContain("Hidden", "Hidden:Hidden nested", "Hidden:Visible nested")
                .contains("Assets:Cash", "Trading:CURRENCY:USD");
    }

    @Test
    void aHiddenAccountStillCountsInItsParentsTotal() {
        sql.update("UPDATE accounts SET hidden = 1 WHERE guid = ?", SampleBook.CASH);

        List<AccountNode> tree = book.accountTree(DUMP_DAY);

        assertThat(flatten(tree).map(AccountNode::path)).doesNotContain("Assets:Cash");
        assertThat(total(tree, "Assets")).isEqualTo("1585.00");
    }

    @Test
    void aForeignChildIsConvertedWithTheLatestPriceAndBankersRounding() {
        // 10.05 USD at 1.3 is 13.065 EUR; desktop's GNC_HOW_RND_ROUND gives 13.06
        usdIntoCashUsd(1005);

        List<AccountNode> tree = book.accountTree(DUMP_DAY);

        assertThat(total(tree, "Assets:Cash USD")).isEqualTo("10.05");
        assertThat(find(tree, "Assets:Cash USD").commodity().mnemonic()).isEqualTo("USD");
        assertThat(total(tree, "Assets")).isEqualTo("1598.06");
        assertThat(total(tree, "Trading")).isEqualTo("-38.06");
    }

    @Test
    void anOlderPriceIsIgnored() {
        sql.update("""
                        INSERT INTO prices VALUES ('00000000000000000000000000000001', ?, ?,
                                                   '2026-10-01 10:59:00', 'user:price', 'last', 2, 1)""",
                SampleBook.USD, SampleBook.EUR);
        usdIntoCashUsd(1000);

        assertThat(total(book.accountTree(DUMP_DAY), "Assets")).isEqualTo("1598.00");
    }

    @Test
    void anInversePriceIsUsedWhenItIsTheOnlyOne() {
        sql.update("""
                        UPDATE prices SET commodity_guid = ?, currency_guid = ?, value_num = 10, value_denom = 13
                        WHERE commodity_guid = ? AND currency_guid = ?""",
                SampleBook.EUR, SampleBook.USD, SampleBook.USD, SampleBook.EUR);
        usdIntoCashUsd(1005);

        assertThat(total(book.accountTree(DUMP_DAY), "Assets")).isEqualTo("1598.06");
    }

    @Test
    void withoutAPriceTheParentShowsNoTotal() {
        sql.update("DELETE FROM prices WHERE commodity_guid = ?", SampleBook.USD);
        usdIntoCashUsd(1005);

        List<AccountNode> tree = book.accountTree(DUMP_DAY);

        assertThat(find(tree, "Assets").total()).isEmpty();
        assertThat(total(tree, "Assets:Cash USD")).isEqualTo("10.05");
        assertThat(total(tree, "Assets:Cash")).isEqualTo("1585.00");
    }

    @Test
    void aForeignChildWithoutMoneyNeedsNoPrice() {
        sql.update("DELETE FROM prices WHERE commodity_guid = ?", SampleBook.USD);

        assertThat(total(book.accountTree(DUMP_DAY), "Assets")).isEqualTo("1585.00");
    }

    /** A USD-only transaction: Cash USD +cents, Trading:CURRENCY:USD −cents, dated 2026-11-04. */
    private void usdIntoCashUsd(long cents) {
        sql.update("INSERT INTO transactions VALUES ('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', ?, '', '2026-11-04 10:59:00', '2026-11-04 12:00:00', 'USD gift')",
                SampleBook.USD);
        for(Object[] split : new Object[][]{{"b1", SampleBook.CASH_USD, cents}, {"b2", SampleBook.TRADING_USD, -cents}}) {
            sql.update("INSERT INTO splits VALUES (?, 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', ?, '', '', 'n', '1970-01-01', ?, 100, ?, 100, NULL)",
                    ((String) split[0]).repeat(16), split[1], split[2], split[2]);
        }
    }
}
