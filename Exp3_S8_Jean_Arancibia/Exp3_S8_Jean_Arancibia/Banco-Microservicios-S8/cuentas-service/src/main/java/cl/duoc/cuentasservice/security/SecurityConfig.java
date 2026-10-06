package cl.duoc.cuentasservice.security;

import static org.springframework.security.authorization.AuthorityAuthorizationManager.*;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  JwtDecoder decoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwks) {
    var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
    OAuth2TokenValidator<Jwt> audience =
        token ->
            token.getAudience().contains("banco-api")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Audiencia incorrecta", null));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), audience));
    return decoder;
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    var scopes = new JwtGrantedAuthoritiesConverter();
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
          var roles = jwt.getClaimAsStringList("roles");
          if (roles != null)
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
          return authorities;
        });
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers("/api/demo/**", "/api/admin/**")
                    .access(
                        AuthorizationManagers.allOf(
                            hasAuthority("SCOPE_banco.admin"), hasRole("ADMIN")))
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST, "/api/reportes/solicitudes")
                    .hasAuthority("SCOPE_reportes.write")
                    .requestMatchers("/api/**")
                    .hasAuthority("SCOPE_cuentas.read")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)))
        .build();
  }
}
