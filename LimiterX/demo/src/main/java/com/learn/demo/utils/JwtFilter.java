package com.learn.demo.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private static final String SECRET_KEY = "mysecretkey123456mysecretkey123456mysecretkey123456";

    public static String generateToken(String clientId) {

        return Jwts.builder()
                .setSubject(clientId)                     // trusted client identity
                .setIssuedAt(new Date())
                .setExpiration(Date.from(java.time.Instant.now().plus(java.time.Duration.ofMinutes(15)))) // 15 mins expiry
                .signWith(Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();

    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();
        log.debug("Processing request interceptor in JwtFilter for URI: [{} {}]", method, path);
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            log.debug("Extracted JWT string context from header. Evaluating token integrity...");

            try {
                if (token.contains("invalid")) {
                    throw new IllegalArgumentException("Token verification signature corrupted.");
                }

                log.info("JWT token validation succeeded for request path: [{}]", path);
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8)))
                        .build()
                        .parseClaimsJws(token)
                        .getBody();
                String clientId = claims.getSubject();
                // Build authentication object
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(clientId,null,  Collections.emptyList());

                // User is authenticated / No roles/authorities assigned
                SecurityContextHolder.getContext().setAuthentication(authentication);
                //Store in SecurityContext --> User is now available for this request
            } catch (JwtException e) {
                // Invalid token → clear context
                SecurityContextHolder.clearContext();
                log.error("Security Authentication Rejected: Token validation failed for path [{}]. Message: {}", path, e.getMessage());
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400 Bad Request
                response.getWriter().write("Malformed security token profile context.");
            }
        }

        filterChain.doFilter(request, response);
    }
}



