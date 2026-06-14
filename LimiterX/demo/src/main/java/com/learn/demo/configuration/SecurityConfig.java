package com.learn.demo.configuration;

import com.learn.demo.utils.JwtFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())   // ✅ disable CSRF properly
                //Since you're using JWT (stateless), CSRF is not needed
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/home/generatetoken/**").permitAll()
                        .requestMatchers("/home/navigation").authenticated() // allow without authentication
                        .anyRequest().authenticated()            // protect everything else
                )

                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // stateless

                .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> { res.setStatus(HttpServletResponse.SC_UNAUTHORIZED); res.setContentType("application/json"); res.getWriter().write("{\"error\":\"Unauthorized\"}"); }))

                .addFilterBefore((Filter) new JwtFilter(), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exception -> exception
                        // Dynamic inline configuration catching core application framework rejections
                        .authenticationEntryPoint(authenticationEntryPoint())
                );
        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            log.error("Unauthorized entry attempt blocked by Filter Security Wrapper layer. URI: [{} {}], Exception Context: {}",
                    request.getMethod(), request.getRequestURI(), authException.getMessage());

            response.setStatus(400); // Forcing 400 matching your localized testing profile
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Bad Request\", \"message\": \"" + authException.getMessage() + "\"}");
        };
    }

}


