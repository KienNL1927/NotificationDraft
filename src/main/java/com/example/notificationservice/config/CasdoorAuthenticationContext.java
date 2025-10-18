package com.example.notificationservice.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class CasdoorAuthenticationContext {

    public Optional<Integer> getCurrentUserId() {
        return getJwt().map(jwt -> {
            // Casdoor typically uses 'name' or 'id' for user identifier
            // First try to get from 'id' claim (if numeric)
            Object idClaim = jwt.getClaim("id");
            if (idClaim != null) {
                try {
                    return Integer.parseInt(idClaim.toString());
                } catch (NumberFormatException e) {
                    log.debug("ID claim is not numeric: {}", idClaim);
                }
            }

            // Try 'userId' claim
            Object userId = jwt.getClaim("userId");
            if (userId != null) {
                try {
                    return Integer.parseInt(userId.toString());
                } catch (NumberFormatException e) {
                    log.debug("userId claim is not numeric: {}", userId);
                }
            }

            // Try to extract from 'sub' (subject)
            String sub = jwt.getSubject();
            if (sub != null) {
                // Casdoor format might be "admin/user_123" or just "123"
                if (sub.contains("/")) {
                    String[] parts = sub.split("/");
                    if (parts.length > 1) {
                        String lastPart = parts[parts.length - 1];
                        if (lastPart.startsWith("user_")) {
                            try {
                                return Integer.parseInt(lastPart.substring(5));
                            } catch (NumberFormatException e) {
                                log.debug("Could not parse user ID from sub: {}", sub);
                            }
                        }
                    }
                }

                // Try parsing sub directly as number
                try {
                    return Integer.parseInt(sub);
                } catch (NumberFormatException e) {
                    // Generate a hash-based ID from username for consistency
                    String username = getCurrentUsername().orElse(sub);
                    return Math.abs(username.hashCode());
                }
            }

            return null;
        });
    }

    public Optional<String> getCurrentUsername() {
        return getJwt().map(jwt -> {
            // Casdoor uses 'name' for username
            String username = jwt.getClaimAsString("name");

            if (username == null) {
                username = jwt.getClaimAsString("preferred_username");
            }

            if (username == null) {
                username = jwt.getClaimAsString("user");
            }

            if (username == null) {
                // Extract from subject if it contains organization/username format
                String sub = jwt.getSubject();
                if (sub != null && sub.contains("/")) {
                    String[] parts = sub.split("/");
                    if (parts.length > 1) {
                        username = parts[1];
                    }
                } else {
                    username = sub;
                }
            }

            return username;
        });
    }

    public Optional<String> getCurrentUserEmail() {
        return getJwt().map(jwt -> {
            String email = jwt.getClaimAsString("email");
            if (email == null) {
                email = jwt.getClaimAsString("emailAddress");
            }
            return email;
        });
    }

    public Optional<String> getCurrentUserPhone() {
        return getJwt().map(jwt -> jwt.getClaimAsString("phone"));
    }

    public Optional<String> getCurrentOrganization() {
        return getJwt().map(jwt -> {
            // Casdoor includes organization in 'owner' or 'organization' claim
            String org = jwt.getClaimAsString("owner");
            if (org == null) {
                org = jwt.getClaimAsString("organization");
            }
            if (org == null) {
                // Try to extract from subject
                String sub = jwt.getSubject();
                if (sub != null && sub.contains("/")) {
                    String[] parts = sub.split("/");
                    if (parts.length > 0) {
                        org = parts[0];
                    }
                }
            }
            return org;
        });
    }

    public List<String> getCurrentUserRoles() {
        return getJwt().map(jwt -> {
            // Casdoor might use different claim names for roles
            List<String> roles = jwt.getClaimAsStringList("roles");

            if (roles == null || roles.isEmpty()) {
                roles = jwt.getClaimAsStringList("groups");
            }

            if (roles == null || roles.isEmpty()) {
                // Check if role is a single string
                String role = jwt.getClaimAsString("role");
                if (role != null) {
                    roles = List.of(role);
                }
            }

            if (roles == null || roles.isEmpty()) {
                // Check tag field which Casdoor uses for roles
                String tag = jwt.getClaimAsString("tag");
                if (tag != null) {
                    if (tag.equalsIgnoreCase("admin") || tag.equalsIgnoreCase("staff")) {
                        roles = List.of("ROLE_ADMIN");
                    } else {
                        roles = List.of("ROLE_USER");
                    }
                }
            }

            // Default to USER role if no roles found
            if (roles == null || roles.isEmpty()) {
                roles = List.of("ROLE_USER");
            }

            // Ensure all roles have ROLE_ prefix for Spring Security
            return roles.stream()
                    .map(r -> r.toUpperCase().startsWith("ROLE_") ? r.toUpperCase() : "ROLE_" + r.toUpperCase())
                    .toList();

        }).orElse(List.of());
    }

    public boolean hasRole(String role) {
        String normalizedRole = role.toUpperCase().startsWith("ROLE_") ?
                role.toUpperCase() : "ROLE_" + role.toUpperCase();
        return getCurrentUserRoles().contains(normalizedRole);
    }

    public boolean isAdmin() {
        // Check multiple possible admin indicators
        return hasRole("ADMIN") ||
                hasRole("ROLE_ADMIN") ||
                hasRole("STAFF") ||
                hasRole("ROLE_STAFF") ||
                isTaggedAsAdmin();
    }

    private boolean isTaggedAsAdmin() {
        return getJwt().map(jwt -> {
            String tag = jwt.getClaimAsString("tag");
            return tag != null && (tag.equalsIgnoreCase("admin") || tag.equalsIgnoreCase("staff"));
        }).orElse(false);
    }

    public boolean isUser() {
        return hasRole("USER") || hasRole("ROLE_USER");
    }

    public Optional<Map<String, Object>> getAllClaims() {
        return getJwt().map(Jwt::getClaims);
    }

    private Optional<Jwt> getJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
            return Optional.of((Jwt) authentication.getPrincipal());
        }
        return Optional.empty();
    }

    public Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    // Debug method to log all claims
    public void debugLogClaims() {
        getAllClaims().ifPresent(claims -> {
            log.debug("JWT Claims:");
            claims.forEach((key, value) ->
                    log.debug("  {} = {} (type: {})", key, value, value != null ? value.getClass().getSimpleName() : "null")
            );
        });
    }
}