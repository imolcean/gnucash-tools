package org.duckdns.gory26.gnucash.togo.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.duckdns.gory26.gnucash.togo.session.BookFailure;
import org.duckdns.gory26.gnucash.togo.session.SessionCloser;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * When a session's pool can't get a connection. A book that is down shows a banner and keeps the page as it
 * was, so a Save can be tapped again; a login the book now refuses (its password changed) logs out. Reads fail
 * as {@link DataAccessException}, writes inside a transaction as {@link TransactionException}.
 */
@ControllerAdvice
class BookFailureAdvice {

    static final String REFUSED_LOGIN_URL = "/login?refused";

    private static final Logger log = LoggerFactory.getLogger(BookFailureAdvice.class);

    private final SessionCloser closer;

    BookFailureAdvice(SessionCloser closer) {
        this.closer = closer;
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    @Nullable ModelAndView bookFailed(Exception e, HttpServletRequest request, HttpServletResponse response) throws Exception {
        BookFailure failure = BookFailure.of(e);
        if (failure == null) {
            throw e;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String user = authentication == null ? null : authentication.getName();
        boolean htmx = request.getHeader("HX-Request") != null;

        if (failure == BookFailure.LOGIN_REFUSED) {
            log.warn("The book refused the login of {}; logged out", user);
            closer.logout(request, response, authentication);
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            if (htmx) {
                response.setHeader("HX-Redirect", REFUSED_LOGIN_URL);
                return null;
            }

            return new ModelAndView("redirect:" + REFUSED_LOGIN_URL);
        }

        log.warn("Cannot reach the book for {}: {}", user, NestedExceptionUtils.getMostSpecificCause(e).getMessage());
        if (htmx) {
            // htmx 2 swaps nothing on an error status, so 200 and point the swap at the banners instead
            // of the element's own target: the page, and anything typed into it, stays as it was.
            response.setHeader("HX-Retarget", "#banners");
            response.setHeader("HX-Reswap", "outerHTML");
            return new ModelAndView("fragments/banners :: banners").addObject("unreachable", true);
        }

        String query = request.getQueryString();
        return new ModelAndView("unreachable", HttpStatus.SERVICE_UNAVAILABLE)
                .addObject("unreachable", true)
                .addObject("user", user)
                .addObject("path", request.getRequestURI())
                .addObject("retry", request.getRequestURI() + (query == null ? "" : "?" + query));
    }
}
