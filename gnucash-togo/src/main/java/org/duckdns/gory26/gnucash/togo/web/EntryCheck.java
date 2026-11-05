package org.duckdns.gory26.gnucash.togo.web;

import org.duckdns.gory26.gnucash.book.AccountSummary;
import org.duckdns.gory26.gnucash.book.AmountParser;
import org.duckdns.gory26.gnucash.book.GncNumeric;
import org.duckdns.gory26.gnucash.book.GnuCashBook;
import org.duckdns.gory26.gnucash.book.InvalidAmountException;
import org.duckdns.gory26.gnucash.book.TransferDraft;
import org.springframework.validation.BindingResult;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Checks an {@link EntryForm} field by field and turns it into a {@link TransferDraft}. Field errors are message
 * codes keyed by field name. What only the book can decide (lock, schema, allowed accounts, same commodity)
 * is left to its {@code WriteRefusedException}.
 *
 * @param from the picked from-account, when it exists
 * @param to   the picked to-account, when it exists
 */
record EntryCheck(Optional<TransferDraft> draft, Map<String, String> errors,
                  Optional<AccountSummary> from, Optional<AccountSummary> to) {

    /** Used to check an amount's shape before any account says how fine it may be. */
    private static final long ANY_FRACTION = 1_000_000_000L;

    /** @param binding the form's binding; a date that didn't parse is a field error like a missing one */
    static EntryCheck of(EntryForm form, BindingResult binding, GnuCashBook book) {
        Map<String, String> errors = new LinkedHashMap<>();
        Optional<AccountSummary> from = account(form.getFrom(), book);
        Optional<AccountSummary> to = account(form.getTo(), book);

        if (form.getDate() == null || binding.hasFieldErrors("date")) {
            errors.put("date", "entry.error.date");
        }
        List<String> tags = form.allTags();
        if (tags.stream().anyMatch(t -> t.contains("[") || t.contains("]"))) {
            errors.put("tags", "entry.error.tagBrackets");
        }
        if (from.isEmpty()) {
            errors.put("from", "entry.error.account");
        }
        if (to.isEmpty()) {
            errors.put("to", "entry.error.account");
        } else if (from.isPresent() && from.get().guid().equals(to.get().guid())) {
            errors.put("to", "entry.error.sameAccount");
        }

        GncNumeric amount = null;
        long fraction = from.or(() -> to).map(a -> a.commodity().fraction()).orElse(ANY_FRACTION);
        if (form.getAmount().isBlank()) {
            errors.put("amount", "entry.error.amount.missing");
        } else {
            try {
                BigDecimal parsed = AmountParser.parse(form.getAmount(), fraction);
                if (parsed.signum() == 0) {
                    errors.put("amount", "entry.error.amount.zero");
                } else {
                    amount = GncNumeric.of(parsed, fraction);
                }
            } catch (InvalidAmountException e) {
                errors.put("amount", "entry.error.amount." + e.reason().name());
            }
        }

        if (!errors.isEmpty()) {
            return new EntryCheck(Optional.empty(), Collections.unmodifiableMap(errors), from, to);
        }
        return new EntryCheck(Optional.of(new TransferDraft(form.getDate(), tags, form.getText(), amount,
                from.orElseThrow().guid(), to.orElseThrow().guid(), form.getNotes())), Map.of(), from, to);
    }

    private static Optional<AccountSummary> account(String guid, GnuCashBook book) {
        return guid.isBlank() ? Optional.empty() : book.account(guid);
    }
}
