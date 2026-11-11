package org.duckdns.gory26.gnucash.book;

/**
 * A currency or security from {@code commodities}.
 *
 * @param fraction smallest unit as a denominator: 100 for cents
 */
public record Commodity(String guid,
                        String namespace,
                        String mnemonic,
                        long fraction) {

    public boolean isCurrency() {
        return "CURRENCY".equals(namespace);
    }
}
