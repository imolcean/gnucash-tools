package org.duckdns.gory26.gnucash.togo.web;

import jakarta.servlet.http.HttpServletResponse;
import org.duckdns.gory26.gnucash.book.GnuCashBook;
import org.duckdns.gory26.gnucash.book.TransferDraft;
import org.duckdns.gory26.gnucash.book.WriteRefusedException;
import org.duckdns.gory26.gnucash.togo.session.Session;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Edit and delete an editable transaction (ADR-0008) on a full page with the entry form. Save, delete and
 * cancel go back to the register the user came from. The book refuses anything else, even from a forged form.
 */
@Controller
class TransactionController {

    private static final Pattern REGISTER = Pattern.compile("/accounts/[0-9a-f]{32}");

    private final EntryView view;
    private final Formats fmt;

    TransactionController(EntryView view, Formats fmt) {
        this.view = view;
        this.fmt = fmt;
    }

    /** Only a register path; anything else could send the user off-site. */
    static String safe(String returnTo) {
        return REGISTER.matcher(returnTo).matches() ? returnTo : "/accounts";
    }

    @GetMapping("/transactions/{tx}/edit")
    String edit(@AuthenticationPrincipal Session session, @PathVariable String tx,
                @RequestParam(defaultValue = "") String returnTo, Model model, HttpServletResponse response) {
        GnuCashBook book = session.book();
        Optional<TransferDraft> draft = book.draft(tx);
        model.addAttribute("returnTo", safe(returnTo));
        if(draft.isEmpty()) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return "not-editable";
        }
        TransferDraft d = draft.get();
        EntryForm form = EntryForm.empty(d.date());
        form.setTags(d.tags());
        form.setText(d.text());
        book.account(d.fromAccountGuid()).ifPresent(from -> form.setAmount(fmt.input(d.amount(), from.commodity())));
        form.setFrom(d.fromAccountGuid());
        form.setTo(d.toAccountGuid());
        form.setNotes(d.notes());
        form.setTxGuid(tx);
        form.setReturnTo(safe(returnTo));
        view.prepare(form, book, model, Map.of());
        return "edit";
    }

    /** On success htmx follows {@code HX-Redirect} back to the register; otherwise the form comes back. */
    @PostMapping("/transactions/{tx}")
    String update(@AuthenticationPrincipal Session session, @PathVariable String tx,
                  @ModelAttribute("form") EntryForm form, BindingResult binding, Model model,
                  HttpServletResponse response) {
        GnuCashBook book = session.book();
        form.setTxGuid(tx);
        EntryCheck check = EntryCheck.of(form, binding, book);
        if(!check.errors().isEmpty()) {
            return view.fragment(form, book, model, check.errors());
        }
        try {
            book.update(tx, check.draft().orElseThrow());
        }
        catch(WriteRefusedException e) {
            model.addAttribute("refusal", e.getMessage());
            return view.fragment(form, book, model, Map.of());
        }
        return back(form, response);
    }

    @PostMapping("/transactions/{tx}/delete")
    String delete(@AuthenticationPrincipal Session session, @PathVariable String tx,
                  @ModelAttribute("form") EntryForm form, Model model, HttpServletResponse response) {
        GnuCashBook book = session.book();
        form.setTxGuid(tx);
        try {
            book.delete(tx);
        }
        catch(WriteRefusedException e) {
            model.addAttribute("refusal", e.getMessage());
            return view.fragment(form, book, model, Map.of());
        }
        return back(form, response);
    }

    private String back(EntryForm form, HttpServletResponse response) {
        response.setHeader("HX-Redirect", safe(form.getReturnTo()));
        return "fragments/suggestions :: none";
    }
}
