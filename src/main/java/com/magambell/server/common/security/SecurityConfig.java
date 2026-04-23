package com.magambell.server.common.security;

import com.magambell.server.user.domain.enums.UserRole;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final Optional<AppVersionCheckFilter> appVersionCheckFilter;

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        String[] permitAllWhiteList = {
                "/h2-console/**",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/api/v1/user/register",
                "/api/v1/verify/email/register/**",
                "/api/v1/verify/nickname/register",
                "/api/v1/verify/social",
                "/api/v1/auth/**",
                "/actuator/**",
                "/magambell/**",
                "/api/v1/payment/**",
                "/api/v1/app-version/**",
                "/favicon.ico",
                "/error"
        };

        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(permitAllWhiteList)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/store/**").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/store/**").permitAll()// todo 추후 admin으로 변경
                        .requestMatchers(HttpMethod.POST, "/api/v1/store").hasRole("OWNER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/review").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/review/rating").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/service-areas").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/banner/images").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/notification/store/*/subscribers").permitAll()
                        .requestMatchers("/admin")
                        .hasRole(UserRole.ADMIN.name())
                        .anyRequest()
                        .authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .headers(headers -> headers.frameOptions(FrameOptionsConfig::sameOrigin));
        
        // AppVersionCheckFilter가 있을 때만 추가
        appVersionCheckFilter.ifPresent(filter -> 
                http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
        );
        
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}
