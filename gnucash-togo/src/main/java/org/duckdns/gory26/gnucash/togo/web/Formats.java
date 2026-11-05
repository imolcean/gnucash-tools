package org.duckdns.gory26.gnucash.togo.web;

import org.duckdns.gory26.gnucash.book.Commodity;
import org.duckdns.gory26.gnucash.book.GncNumeric;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * How amounts and dates read on the phone: {@code 1 585.00 €}, {@code −25.00 €}, {@code 31.05.2026}.
 * Exact throughout (ADR-0003): amounts go through {@link GncNumeric#toBigDecimal()} and are never rounded;
 * an amount finer than its commodity simply shows more decimals. Templates call it as {@code @fmt}.
 */
@Component("fmt")
public class Formats {

    static final char GROUP = ' ';   // narrow no-break space
    static final char BEFORE_SYMBOL = ' ';
    static final char MINUS = '−';

    private static final Map<String, String> SYMBOLS = Map.of("EUR", "€", "USD", "$");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);

    public String amount(GncNumeric amount, Commodity commodity) {
        return format(amount, commodity, false);
    }

    /** With a {@code +} on positive amounts, for register rows. */
    public String signed(GncNumeric amount, Commodity commodity) {
        return format(amount, commodity, true);
    }

    /** The amount as the entry form's input shows it: {@code 1234.56}. */
    public String input(GncNumeric amount, Commodity commodity) {
        return decimal(amount, commodity).toPlainString();
    }

    public String symbol(Commodity commodity) {
        return symbol(commodity.mnemonic());
    }

    /** {@code €}, {@code $}, or the mnemonic itself. */
    public String symbol(String mnemonic) {
        return SYMBOLS.getOrDefault(mnemonic, mnemonic);
    }

    public String date(LocalDate date) {
        return DATE.format(date);
    }

    public String weekday(LocalDate date) {
        return WEEKDAY.format(date);
    }

    private String format(GncNumeric amount, Commodity commodity, boolean plus) {
        BigDecimal value = decimal(amount, commodity);
        String digits = value.abs().toPlainString();
        int point = digits.indexOf('.');
        String integer = point < 0 ? digits : digits.substring(0, point);
        String fraction = point < 0 ? "" : digits.substring(point);
        String sign = value.signum() < 0 ? String.valueOf(MINUS) : plus && value.signum() > 0 ? "+" : "";
        return sign + group(integer) + fraction + BEFORE_SYMBOL + symbol(commodity);
    }

    /** Padded to the commodity's decimals when its fraction is a power of ten; never rounded. */
    private static BigDecimal decimal(GncNumeric amount, Commodity commodity) {
        BigDecimal value = amount.toBigDecimal();
        int decimals = decimalsOf(commodity.fraction());
        return decimals >= 0 && value.scale() < decimals ? value.setScale(decimals, RoundingMode.UNNECESSARY) : value;
    }

    /** 100 → 2, 1 → 0; -1 when the fraction is not a power of ten. */
    private static int decimalsOf(long fraction) {
        int decimals = 0;
        for (long f = fraction; f > 1; f /= 10) {
            if (f % 10 != 0) {
                return -1;
            }
            decimals++;
        }
        return decimals;
    }

    private static String group(String integer) {
        StringBuilder out = new StringBuilder();
        int lead = integer.length() % 3 == 0 ? 3 : integer.length() % 3;
        out.append(integer, 0, lead);
        for (int i = lead; i < integer.length(); i += 3) {
            out.append(GROUP).append(integer, i, i + 3);
        }
        return out.toString();
    }
}
