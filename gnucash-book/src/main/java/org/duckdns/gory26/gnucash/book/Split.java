package org.duckdns.gory26.gnucash.book;

/**
 * One leg of a {@link Transaction}: a signed {@code value} in the transaction currency (positive = debit)
 * and a signed {@code quantity} in the account's commodity.
 */
public record Split(String accountGuid,
                    GncNumeric value,
                    GncNumeric quantity) {
}
