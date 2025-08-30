package com.UsdtWallet.UsdtWallet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.UsdtWallet.UsdtWallet.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // Public endpoints - không cần authentication
                .requestMatchers("/api/auth/register").permitAll()
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/auth/check-username").permitAll()
                .requestMatchers("/api/auth/check-email").permitAll()
                .requestMatchers("/api/auth/create-admin").permitAll() // Allow admin creation
                .requestMatchers("/api/admin/wallet/**").permitAll() // Temporary for testing
                .requestMatchers("/api/test/**").permitAll() // Allow all test endpoints
                .requestMatchers("/api/dev/**").permitAll() // Allow dev endpoints for development

                // Health check endpoints
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/health").permitAll()

                // API documentation endpoints
                .requestMatchers("/v3/api-docs/**").permitAll()
                .requestMatchers("/swagger-ui/**").permitAll()
                .requestMatchers("/swagger-ui.html").permitAll()

                // Static resources
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers("/.well-known/**").permitAll()

                // All other requests require authentication
                .anyRequest().authenticated()
            );

        // IMPORTANT: Only add JWT filter for endpoints that need authentication
        // Skip JWT filter for public endpoints
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
