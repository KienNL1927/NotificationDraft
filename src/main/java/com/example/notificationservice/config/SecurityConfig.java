package com.example.notificationservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.*;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    // === 1) Chain cho ACTUATOR: permitAll ===
    @Bean
    @Order(0)
    public SecurityFilterChain actuatorChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(rc -> rc.disable())
                .securityContext(sc -> sc.disable());
        return http.build();
    }

    // === 2) Chain cho ứng dụng: yêu cầu JWT ===
    @Bean
    @Order(1)
    public SecurityFilterChain appChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        // public endpoints
                        .requestMatchers("/ws/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // admin endpoints
                        .requestMatchers("/api/v1/templates/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/redis/test/**").hasRole("ADMIN")

                        // user endpoints
                        .requestMatchers("/api/v1/preferences/**").authenticated()
                        .requestMatchers("/api/v1/sse/**").authenticated()
                        .requestMatchers("/api/v1/auth/test/**").authenticated()

                        // any other request requires authentication
                        .anyRequest().authenticated()
                )
                // ENABLE OAuth2 JWT validation
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );
        return http.build();
    }

    // === JWT Decoder ===
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    // === JWT Authentication Converter ===
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // Standard scopes converter
        JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();
        scopesConverter.setAuthoritiesClaimName("scope");
        scopesConverter.setAuthorityPrefix("SCOPE_");

        Converter<Jwt, Collection<GrantedAuthority>> custom = jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();

            // 1) Add scopes from "scope" claim
            authorities.addAll(scopesConverter.convert(jwt));

            // 1b) Add scopes from "scp" claim if present
            Object scp = jwt.getClaims().get("scp");
            if (scp instanceof Collection<?> c) {
                for (Object it : c) {
                    if (it instanceof String s && !s.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority("SCOPE_" + s));
                    }
                }
            } else if (scp instanceof String s) {
                for (String p : s.split("\\s+|,")) {
                    if (!p.isBlank()) authorities.add(new SimpleGrantedAuthority("SCOPE_" + p.trim()));
                }
            }

            // 2) Extract roles from Casdoor's "roles" claim
            Object rolesObj = jwt.getClaims().get("roles");
            normalizeRoles(rolesObj).forEach(r -> authorities.add(new SimpleGrantedAuthority(r)));

            // 3) Check Casdoor-specific fields: tag, isAdmin
            String tag = jwt.getClaimAsString("tag");
            Boolean isAdmin = jwt.getClaim("isAdmin");
            if ("staff".equalsIgnoreCase(tag) || "admin".equalsIgnoreCase(tag) || Boolean.TRUE.equals(isAdmin)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }

            // 4) Default role if none found
            boolean hasRole = authorities.stream().anyMatch(a -> a.getAuthority().startsWith("ROLE_"));
            if (!hasRole) authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

            // Remove duplicates
            return authorities.stream().distinct().collect(Collectors.toList());
        };

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(custom);
        return converter;
    }

    // === CORS Configuration ===
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("*")); // Use patterns instead of origins
        cfg.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }

    // ==== Helper Methods ====

    /**
     * Normalize roles claim to ROLE_* format (case insensitive)
     */
    private static List<String> normalizeRoles(Object rolesClaim) {
        List<String> out = new ArrayList<>();
        if (rolesClaim instanceof Collection<?> coll) {
            for (Object it : coll) {
                if (it instanceof String s) {
                    addRole(out, s);
                } else if (it instanceof Map<?,?> m) {
                    // Case-insensitive key lookup
                    Object v = getMapValueIgnoreCase(m, "name", "role", "authority",
                            "value", "code", "key", "displayname");
                    if (v instanceof String s) {
                        addRole(out, s);
                    }
                }
            }
        } else if (rolesClaim instanceof String s) {
            addRole(out, s);
        }
        return out;
    }

    /**
     * Get value from map with case-insensitive key lookup
     */
    private static Object getMapValueIgnoreCase(Map<?,?> map, String... keys) {
        Map<String, Object> lowerCaseMap = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                lowerCaseMap.put(key.toLowerCase(Locale.ROOT), entry.getValue());
            }
        }

        for (String key : keys) {
            Object value = lowerCaseMap.get(key.toLowerCase(Locale.ROOT));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static void addRole(List<String> out, String raw) {
        String r = normalizeRoleName(raw);
        if (r != null) out.add(r);
    }

    private static String normalizeRoleName(String name) {
        if (name == null) return null;
        String r = name.trim();
        if (r.isEmpty()) return null;

        // Remove non-alphanumeric and convert to uppercase
        r = r.replaceAll("[^a-zA-Z0-9]+", "_").toUpperCase(Locale.ROOT);

        // Ensure ROLE_ prefix
        if (!r.startsWith("ROLE_")) r = "ROLE_" + r;

        return r;
    }
}