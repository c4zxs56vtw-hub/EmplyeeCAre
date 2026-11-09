package com.employeecare.api.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

import org.springframework.security.web.SecurityFilterChain;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            DatabaseJwtAuthenticationConverter jwtConverter,
            PublicEndpointRateLimitFilter publicEndpointRateLimitFilter
    ) throws Exception {

        http

                // API avec JWT Bearer, sans session HTTP
                .csrf(AbstractHttpConfigurer::disable)

                .cors(cors -> cors.configurationSource(
                        corsConfigurationSource()
                ))

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // Désactiver les mécanismes de connexion classiques
                .formLogin(AbstractHttpConfigurer::disable)

                .httpBasic(AbstractHttpConfigurer::disable)

                // Limitation du débit sur les routes publiques
                .addFilterBefore(
                        publicEndpointRateLimitFilter,
                        BearerTokenAuthenticationFilter.class
                )

                // Configurer les autorisations
                .authorizeHttpRequests(auth -> auth

                        // Requêtes CORS
                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        ).permitAll()

                        // Routes publiques d'authentification
                        .requestMatchers(
                                HttpMethod.POST,
                                "/auth/login",
                                "/auth/activate-account",
                                "/auth/forgot-password"
                        ).permitAll()

                        // Signalement anonyme public
                        .requestMatchers(
                                HttpMethod.POST,
                                "/public/tickets/anonymous"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/public/tickets/anonymous/lookup"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/public/tickets/anonymous/review"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/public/tickets/anonymous/close"
                        ).permitAll()

                        // Administration
                        .requestMatchers("/admin/**")
                        .hasRole("ADMINISTRATEUR")

                        // Responsables
                        .requestMatchers("/responsable/**")
                        .hasRole("RESPONSABLE")

                        // Employés
                        .requestMatchers("/employe/**")
                        .hasRole("EMPLOYE")

                        // Session authentifiée
                        .requestMatchers(
                                "/auth/me",
                                "/auth/logout"
                        ).authenticated()

                        // Refuser toutes les autres routes
                        .anyRequest().denyAll()
                )

                // Activer l'authentification JWT
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        jwtConverter
                                )
                        )
                );

        return http.build();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of(
                        "http://localhost:3000",
                        "http://localhost:5173"
                )
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type"
                )
        );

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }
}
