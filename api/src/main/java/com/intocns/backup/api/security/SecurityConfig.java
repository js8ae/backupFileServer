package com.intocns.backup.api.security;

import com.intocns.backup.api.admin.AdminAuthFilter;
import com.intocns.backup.api.error.ErrorCode;
import com.intocns.backup.domain.port.TokenParser;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final TokenParser tokenParser;
    private final String adminKey;

    public SecurityConfig(TokenParser tokenParser,
                          @Value("${backup.admin.key}") String adminKey) {
        this.tokenParser = tokenParser;
        this.adminKey = adminKey;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth/token").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/files/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/prometheus", "/actuator/flyway").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        ErrorCode.INVALID_ARGUMENT, "Authentication required"))
                        .accessDeniedHandler((request, response, e) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                                        ErrorCode.SESSION_FORBIDDEN, "Access denied"))
                )
                .addFilterBefore(new AdminAuthFilter(adminKey), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthFilter(tokenParser), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(HttpServletResponse response, int status, ErrorCode errorCode, String message) {
        try {
            response.setStatus(status);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    String.format("{\"code\":%d,\"message\":\"%s\"}", errorCode.code(), message));
        } catch (Exception ignored) {
        }
    }
}
