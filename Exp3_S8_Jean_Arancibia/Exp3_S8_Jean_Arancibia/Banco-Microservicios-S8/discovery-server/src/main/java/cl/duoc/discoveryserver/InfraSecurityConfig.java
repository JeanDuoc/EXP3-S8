package cl.duoc.discoveryserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class InfraSecurityConfig {
  @Bean
  BCryptPasswordEncoder infraPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService infraUsers(
      @Value("${infra.user}") String user,
      @Value("${infra.password}") String password,
      BCryptPasswordEncoder encoder) {
    return new InMemoryUserDetailsManager(
        User.withUsername(user).password(encoder.encode(password)).roles("INFRA").build());
  }

  @Bean
  SecurityFilterChain infraSecurity(HttpSecurity http) throws Exception {
    return http.csrf(c -> c.disable())
        .authorizeHttpRequests(
            a -> a.requestMatchers("/actuator/health").permitAll().anyRequest().authenticated())
        .httpBasic(Customizer.withDefaults())
        .build();
  }
}
