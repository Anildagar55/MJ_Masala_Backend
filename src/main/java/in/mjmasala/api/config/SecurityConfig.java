package in.mjmasala.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.mjmasala.api.security.AppUserDetailsService;
import in.mjmasala.api.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Stateless JWT security.
 *
 * The API carries no session cookie, so CSRF protection is switched off deliberately —
 * there is no ambient credential for a cross-site request to ride on. Authorisation is
 * expressed once here rather than scattered across controllers.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public SecurityConfig(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // --- public: accounts and the catalogue ---
                        // everything needed to get an account into existence and back
                        // again. Listed one by one rather than /auth/**, because /auth/me
                        // must stay behind the filter.
                        .requestMatchers("/auth/login", "/auth/register", "/auth/refresh",
                                "/auth/verify", "/auth/resend",
                                "/auth/forgot-password", "/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/products/**", "/combos/**", "/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/shipping/**").permitAll()
                        // product photography: public so the browser can cache it
                        .requestMatchers(HttpMethod.GET, "/media/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/coupons/validate").permitAll()
                        .requestMatchers(HttpMethod.POST, "/wholesale/enquiries", "/contact/messages").permitAll()

                        // --- tooling ---
                        .requestMatchers("/actuator/health", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // --- back office ---
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // everything else (cart, wishlist, orders, addresses, me,
                        // writing a review, marking helpful) needs a valid token
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                write(res, HttpServletResponse.SC_UNAUTHORIZED,
                                        "Sign in to continue", req.getRequestURI()))
                        .accessDeniedHandler((req, res, e) ->
                                write(res, HttpServletResponse.SC_FORBIDDEN,
                                        "You do not have access to this", req.getRequestURI())))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void write(HttpServletResponse res, int status, String message, String path)
            throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(res.getOutputStream(), Map.of(
                "status", status,
                "error", message,
                "path", path));
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = properties.cors() != null ? properties.cors().allowedOrigins() : null;
        if (origins == null || origins.isEmpty()) {
            origins = List.of("http://localhost:5173");
        }
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(AppUserDetailsService users,
                                                            PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(users);
        provider.setPasswordEncoder(encoder);
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }
}
