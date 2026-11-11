package org.duckdns.gory26.gnucash.togo.session;

import com.zaxxer.hikari.pool.HikariPool.PoolInitializationException;
import org.duckdns.gory26.gnucash.togo.config.TogoDbProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Passthrough login (ADR-0006): the login succeeds iff Postgres accepts the username and password. There is
 * no user table; the returned authentication carries the user's {@link Session} and no password.
 */
@Component
public class BookAuthenticationProvider implements AuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(BookAuthenticationProvider.class);

    private final Sessions sessions;
    private final TogoDbProperties db;

    public BookAuthenticationProvider(Sessions sessions, TogoDbProperties db) {
        this.sessions = sessions;
        this.db = db;
    }

    @Override
    public Authentication authenticate(Authentication login) throws AuthenticationException {
        if(!db.configured()) {
            throw new AuthenticationServiceException("GNUCASH_DB_HOST and GNUCASH_DB_NAME are not set");
        }
        String username = login.getName();
        String password = String.valueOf(login.getCredentials());
        String from = clientAddress(login);
        // One line per login, never a stack trace: the message says enough.
        try {
            Session session = sessions.open(username, password);
            log.info("Login ok: {} from {}, pool {}", username, from, session.poolName());
            return UsernamePasswordAuthenticationToken.authenticated(session, null, List.of());
        }
        catch(PoolInitializationException e) {
            if(BookFailure.of(e) == BookFailure.LOGIN_REFUSED) {
                log.warn("Login refused: {} from {}", username, from);
                throw new BadCredentialsException("Postgres refused the login", e);
            }
            // The cause stays in the server log; the login page only says the book can't be reached.
            log.warn("Login failed, cannot reach the book: {} from {}: {}", username, from, e.getMessage());
            throw new AuthenticationServiceException("Cannot reach the book", e);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    /** The device's IP, which the form login filter puts into the authentication's details. */
    private static String clientAddress(Authentication login) {
        return login.getDetails() instanceof WebAuthenticationDetails details && details.getRemoteAddress() != null
                ? details.getRemoteAddress()
                : "unknown";
    }
}
