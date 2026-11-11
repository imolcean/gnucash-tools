package org.duckdns.gory26.gnucash.togo.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;

/** Closes a user's pool when they log out or their HTTP session is destroyed. */
@Component
public class SessionCloser implements LogoutHandler, ApplicationListener<HttpSessionDestroyedEvent> {

    private final Sessions sessions;

    public SessionCloser(Sessions sessions) {
        this.sessions = sessions;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, @Nullable Authentication authentication) {
        close(authentication);
    }

    @Override
    public void onApplicationEvent(HttpSessionDestroyedEvent event) {
        for(SecurityContext context : event.getSecurityContexts()) {
            close(context.getAuthentication());
        }
    }

    private void close(@Nullable Authentication authentication) {
        if(authentication != null && authentication.getPrincipal() instanceof Session session) {
            sessions.close(session);
        }
    }
}
