package org.duckdns.gory26.gnucash.togo.web;

import jakarta.servlet.http.HttpServletRequest;
import org.duckdns.gory26.gnucash.book.LockHolder;
import org.duckdns.gory26.gnucash.book.SchemaStatus;
import org.duckdns.gory26.gnucash.togo.session.Session;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * What every page needs: who is logged in, where we are, and whether the book can be written. The lock
 * (ADR-0004) and schema (ADR-0005) banners disable write buttons; the server re-checks on every write anyway.
 */
@ControllerAdvice
class BannerAdvice {

    @ModelAttribute
    void banners(@AuthenticationPrincipal Session session, HttpServletRequest request, Model model) {
        model.addAttribute("path", request.getRequestURI());
        if (session == null) {
            return;
        }
        LockHolder lock = session.book().lock().orElse(null);
        SchemaStatus schema = session.book().schemaStatus();
        model.addAttribute("user", session.getName());
        model.addAttribute("lock", lock);
        model.addAttribute("schema", schema);
        model.addAttribute("writable", lock == null && schema.known());
    }
}
