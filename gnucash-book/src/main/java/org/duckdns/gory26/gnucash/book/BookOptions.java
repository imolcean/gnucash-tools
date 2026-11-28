package org.duckdns.gory26.gnucash.book;

/**
 * The book options (desktop: File &gt; Properties) that decide how entries may be written, from the book's
 * {@code options/Accounts/…} slots. A missing slot reads as desktop's default.
 *
 * @param useTradingAccounts     <em>Use Trading Accounts</em>: cross-commodity transactions carry trading splits
 * @param readOnlyThresholdDays  <em>Day Threshold for Read-Only Transactions (red line)</em>: desktop makes
 *                               transactions posted longer ago than this read-only; 0 = off
 */
public record BookOptions(boolean useTradingAccounts,
                          int readOnlyThresholdDays) {
}
