package org.duckdns.gory26.gnucash.togo.web;

import org.duckdns.gory26.gnucash.book.*;
import org.duckdns.gory26.gnucash.togo.session.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An account's register, newest first and grouped by day under sticky headers that carry the end-of-day balance
 * (variant B of {@code prototype/register-prototype.html}). "Load more" appends the next page.
 */
@Controller
class RegisterController {

    /** A day's header and its rows; {@code header} is false for a day continued from the previous page. */
    record Day(boolean header,
               String weekday,
               String date,
               boolean future,
               String balance,
               boolean negative,
               List<Item> items) {
    }

    /**
     * @param other        the other account's last name, or {@code — split (n) —}
     * @param otherFull    the other accounts' full paths, for the tooltip
     * @param reconcileKey the message naming the reconcile state, for its tooltip; null for a state GnuCash doesn't have
     */
    record Item(String txGuid,
                boolean editable,
                boolean future,
                List<String> tags,
                String text,
                String other,
                String otherFull,
                String notes,
                String amount,
                boolean negative,
                String reconcile,
                String reconcileKey) {
    }
    private final Formats fmt;
    private final Clock clock;
    private final int pageSize;

    RegisterController(Formats fmt, Clock clock, @Value("${togo.register.page-size}") int pageSize) {
        this.fmt = fmt;
        this.clock = clock;
        this.pageSize = pageSize;
    }

    private static AccountSummary account(Session session, String guid) {
        return session.book().account(guid).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** GnuCash's reconcile states (n, c, y, f, v), each with its message. */
    private static String reconcileKey(String state) {
        return switch(state) {
            case "n" -> "register.reconcile.n";
            case "c" -> "register.reconcile.c";
            case "y" -> "register.reconcile.y";
            case "f" -> "register.reconcile.f";
            case "v" -> "register.reconcile.v";
            default -> null;
        };
    }

    @GetMapping("/accounts/{guid}")
    String register(@AuthenticationPrincipal Session session, @PathVariable String guid, Model model) {
        AccountSummary account = account(session, guid);
        String path = account.path();
        model.addAttribute("account", account);
        model.addAttribute("parentPath", path.length() > account.name().length()
                ? path.substring(0, path.length() - account.name().length()) : "");
        model.addAttribute("present", fmt.amount(account.displayBalance(), account.commodity()));
        model.addAttribute("presentNegative", account.displayBalance().signum() < 0);
        page(session.book().register(guid, pageSize), account, null, false, model);
        return "register";
    }

    /**
     * The next page of rows, appended to the register.
     *
     * @param lastDate the day of the last row already shown; a day that continues gets no second header
     */
    @GetMapping("/accounts/{guid}/rows")
    String more(@AuthenticationPrincipal Session session, @PathVariable String guid,
                @RequestParam String after, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Optional<LocalDate> lastDate, Model model) {
        AccountSummary account = account(session, guid);
        RegisterCursor cursor;
        try {
            cursor = RegisterCursor.fromToken(after);
        }
        catch(IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        page(session.book().register(guid, cursor, pageSize), account, lastDate.orElse(null), true, model);
        return "register :: more";
    }

    private void page(RegisterPage page, AccountSummary account, LocalDate continuing, boolean more, Model model) {
        LocalDate today = LocalDate.now(clock);
        Commodity commodity = account.commodity();
        List<Day> days = new ArrayList<>();
        LocalDate current = null;
        List<Item> items = null;
        for(RegisterRow row : page.rows()) {
            if(!row.date().equals(current)) {
                current = row.date();
                items = new ArrayList<>();
                GncNumeric endOfDay = account.type().display(row.balance());
                days.add(new Day(!current.equals(continuing), fmt.weekday(current), fmt.date(current),
                        current.isAfter(today), fmt.amount(endOfDay, commodity), endOfDay.signum() < 0, items));
            }
            items.add(item(row, account, today));
        }
        model.addAttribute("days", days);
        model.addAttribute("more", more);
        model.addAttribute("next", page.next().map(RegisterCursor::toToken).orElse(null));
        model.addAttribute("lastDate", current == null ? continuing : current);
        model.addAttribute("guid", account.guid());
    }

    private Item item(RegisterRow row, AccountSummary account, LocalDate today) {
        TaggedDescription description = TaggedDescription.parse(row.description());
        List<String> others = row.otherAccounts();
        String other = switch(others.size()) {
            case 0 -> "—";
            case 1 -> others.getFirst().substring(others.getFirst().lastIndexOf(':') + 1);
            default -> "— split (" + (others.size() + 1) + ") —";
        };
        GncNumeric amount = account.type().display(row.amount());
        return new Item(row.txGuid(), row.editable(), row.date().isAfter(today), description.tags(), description.text(),
                other, String.join(", ", others), row.notes(), fmt.signed(amount, account.commodity()),
                amount.signum() < 0, row.reconcileState(), reconcileKey(row.reconcileState()));
    }
}
