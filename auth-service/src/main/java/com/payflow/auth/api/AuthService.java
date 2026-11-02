package com.payflow.auth.api;

import com.payflow.auth.api.AuthDtos.LoginRequest;
import com.payflow.auth.api.AuthDtos.RegisterRequest;
import com.payflow.auth.api.AuthDtos.TokenResponse;
import com.payflow.auth.security.JwtService;
import com.payflow.auth.user.AppUser;
import com.payflow.auth.user.AppUserRepository;
import com.payflow.auth.user.Role;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (users.existsByUsername(request.username())) {
            throw new DuplicateUserException("Username already taken");
        }
        if (users.existsByEmail(request.email())) {
            throw new DuplicateUserException("Email already registered");
        }
        // Unique constraints still protect us if two requests race past the checks above.
        AppUser saved = users.save(new AppUser(
                request.username(), request.email().toLowerCase(),
                passwordEncoder.encode(request.password()), Role.CUSTOMER));
        return toToken(saved);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        // Throws BadCredentialsException / DisabledException -> mapped to 401
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        AppUser user = users.findByUsername(request.username()).orElseThrow();
        return toToken(user);
    }

    private TokenResponse toToken(AppUser user) {
        return new TokenResponse(jwtService.issue(user), "Bearer", jwtService.ttlSeconds(),
                user.getId(), user.getUsername(), user.getRole().name());
    }

    public static class DuplicateUserException extends RuntimeException {
        public DuplicateUserException(String message) {
            super(message);
        }
    }
}
