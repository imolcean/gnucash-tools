package org.duckdns.gory26.gnucash.togo.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class LoginController {

    /** Tells a wrong password apart from a book that can't be reached, and says why a session ended. */
    @GetMapping("/login")
    String login(HttpServletRequest request, Model model) {
        if(request.getParameterMap().containsKey("error")) {
            HttpSession session = request.getSession(false);
            Object failure = session == null ? null : session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
            boolean refused = failure == null || failure instanceof BadCredentialsException;
            model.addAttribute("error", refused ? "login.error.credentials" : "login.error.unreachable");
        }
        model.addAttribute("loggedOut", request.getParameterMap().containsKey("logout"));
        // BookFailureAdvice logged the session out because its password stopped working.
        model.addAttribute("refused", request.getParameterMap().containsKey("refused"));
        return "login";
    }
}
