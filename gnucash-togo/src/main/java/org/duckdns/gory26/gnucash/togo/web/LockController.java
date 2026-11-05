package org.duckdns.gory26.gnucash.togo.web;

import jakarta.servlet.http.HttpServletResponse;
import org.duckdns.gory26.gnucash.togo.session.Session;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Clear lock (ADR-0004): the page already asked, naming the host and PID. */
@Controller
class LockController {

    @PostMapping("/lock/clear")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clear(@AuthenticationPrincipal Session session, HttpServletResponse response) {
        session.book().clearLock();
        response.setHeader("HX-Refresh", "true");
    }
}
