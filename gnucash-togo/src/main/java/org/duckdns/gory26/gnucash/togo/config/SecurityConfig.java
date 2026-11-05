package org.duckdns.gory26.gnucash.togo.config;

import org.duckdns.gory26.gnucash.togo.session.SessionCloser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Form login against Postgres (ADR-0006). CSRF stays on; htmx sends the token as a header from
 * {@code hx-headers} on {@code <body>}.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SessionCloser closer) {
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(
                                "/login",
                                "/error",
                                "/webjars/**",
                                "/css/**",
                                "/icons/**",
                                "/manifest.webmanifest",
                                "/actuator/health"
                        ).permitAll()
                        .anyRequest().authenticated())
                .formLogin(login -> login.loginPage("/login").permitAll())
                .logout(logout -> logout.addLogoutHandler(closer).logoutSuccessUrl("/login?logout"))
                .build();
    }

    /**
     * Publishes {@code HttpSessionDestroyedEvent}, which closes the session's pool.
     */
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
