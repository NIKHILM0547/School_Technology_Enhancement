package com.eduadmin.school.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/h2-console/**").permitAll()
                // Fees: admins manage everything, students can view/pay their own
                .requestMatchers("/fees/structure/**").hasRole("admin")
                .requestMatchers("/fees/**").hasAnyRole("admin", "student")
                // Only admins can manage users
                .requestMatchers("/users/**").hasRole("admin")
                // Attendance: admins + teachers can mark student attendance,
                // staff attendance is admin-only, students can only view their own
                .requestMatchers("/attendance/rollcall-staff", "/attendance/save-staff").hasRole("admin")
                .requestMatchers("/attendance/rollcall", "/attendance/save",
                        "/attendance/{id}/edit").hasAnyRole("admin", "teacher")
                .requestMatchers("/attendance/leave/apply").hasAnyRole("admin", "teacher", "student")
                .requestMatchers("/attendance/leave/{id}/review").hasRole("admin")
                .requestMatchers("/attendance/**").hasAnyRole("admin", "teacher", "student")
                // Notes: all authenticated roles; access control enforced in the controller
                .requestMatchers("/notes/**").hasAnyRole("admin", "teacher", "student")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .permitAll()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            // H2 console renders in a frame; relax frame options only for it
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"));

        return http.build();
    }
}
