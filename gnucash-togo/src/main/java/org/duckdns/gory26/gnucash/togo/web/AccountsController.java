package org.duckdns.gory26.gnucash.togo.web;

import jakarta.servlet.http.HttpSession;
import org.duckdns.gory26.gnucash.book.AccountNode;
import org.duckdns.gory26.gnucash.book.GncNumeric;
import org.duckdns.gory26.gnucash.togo.session.Session;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

/** The account tree with Present balances, as desktop's Present column shows them (ADR-0008). */
@Controller
class AccountsController {

    /**
     * One line of the tree, formatted.
     *
     * @param own the account's own balance, shown only when it is not zero and differs from the total; or null
     */
    record Row(String guid,
               String name,
               String path,
               boolean placeholder,
               String total,
               boolean negative,
               String own,
               List<Row> children) {
    }
    static final String HIDE_ZERO = "togo.hideZero";
    private final Formats fmt;

    AccountsController(Formats fmt) {
        this.fmt = fmt;
    }

    /**
     * @param hideZero switches hiding zero-balance accounts; the choice stays for the session, on by default
     */
    @GetMapping("/accounts")
    String accounts(@AuthenticationPrincipal Session session, @RequestParam Optional<Boolean> hideZero,
                    HttpSession http, Model model) {
        hideZero.ifPresent(hide -> http.setAttribute(HIDE_ZERO, hide));
        boolean hide = !Boolean.FALSE.equals(http.getAttribute(HIDE_ZERO));
        model.addAttribute("hideZero", hide);
        model.addAttribute("tree", rows(session.book().accountTree(), hide));
        return "accounts";
    }

    private List<Row> rows(List<AccountNode> nodes, boolean hideZero) {
        return nodes.stream()
                .map(node -> row(node, hideZero))
                .flatMap(Optional::stream)
                .toList();
    }

    /** Empty when zero balances are hidden and nothing in this subtree has money. */
    private Optional<Row> row(AccountNode node, boolean hideZero) {
        List<Row> children = rows(node.children(), hideZero);
        Optional<GncNumeric> total = node.displayTotal();
        if(hideZero && total.isPresent() && total.get().isZero() && children.isEmpty()) {
            return Optional.empty();
        }
        GncNumeric own = node.displayBalance();
        boolean showOwn = !own.isZero() && (total.isEmpty() || total.get().compareTo(own) != 0);
        return Optional.of(new Row(node.guid(), node.name(), node.path(), node.placeholder(),
                total.map(t -> fmt.amount(t, node.commodity())).orElse(null),
                total.map(t -> t.signum() < 0).orElse(false),
                showOwn ? fmt.amount(own, node.commodity()) : null,
                children));
    }
}
