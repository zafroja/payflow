package com.payflow.transaction.security;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/** The caller, as asserted by the API gateway after it validated the JWT. */
public record AuthenticatedUser(Long userId, String username, Collection<? extends GrantedAuthority> authorities) {

    public boolean isAdmin() {
        return authorities.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
