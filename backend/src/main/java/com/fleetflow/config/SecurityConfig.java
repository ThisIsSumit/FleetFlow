package com.fleetflow.config;
import com.fleetflow.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import java.util.List;
@Configuration @EnableMethodSecurity
public class SecurityConfig {
  @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
  @Bean SecurityFilterChain chain(HttpSecurity http, JwtAuthFilter jwt) throws Exception {
    http.csrf(c -> c.disable()) // stateless bearer API; refresh cookie is SameSite=Strict and scoped to /api/v1/auth
        .cors(c -> {})
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(h -> h.contentTypeOptions(o -> {}).frameOptions(f -> f.deny())
            .httpStrictTransportSecurity(s -> s.includeSubDomains(true).maxAgeInSeconds(31536000)))
        .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .authorizeHttpRequests(a -> a
            .requestMatchers(HttpMethod.POST, "/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
                "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password", "/api/v1/auth/verify-email", "/api/v1/auth/accept-invitation").permitAll()
            .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/ws/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
            .anyRequest().authenticated())
        .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
  @Bean CorsConfigurationSource cors(@Value("${fleetflow.cors-origins}") List<String> origins) {
    var c = new CorsConfiguration();
    c.setAllowedOrigins(origins); c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
    c.setAllowedHeaders(List.of("Authorization","Content-Type","X-Correlation-Id")); c.setAllowCredentials(true);
    var s = new UrlBasedCorsConfigurationSource(); s.registerCorsConfiguration("/**", c); return s;
  }
}
