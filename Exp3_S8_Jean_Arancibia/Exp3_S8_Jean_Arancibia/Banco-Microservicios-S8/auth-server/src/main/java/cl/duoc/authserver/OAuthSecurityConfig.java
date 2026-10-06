package cl.duoc.authserver;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.*;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.*;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

@Configuration
public class OAuthSecurityConfig {
  @Bean
  @Order(1)
  SecurityFilterChain oauth(HttpSecurity http) throws Exception {
    var oauth = OAuth2AuthorizationServerConfigurer.authorizationServer();
    return http.securityMatcher(oauth.getEndpointsMatcher())
        .with(oauth, c -> c.oidc(Customizer.withDefaults()))
        .authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .exceptionHandling(
            e ->
                e.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
        .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
        .build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain login(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(
            a ->
                a.requestMatchers("/actuator/health", "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(Customizer.withDefaults())
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  UserDetailsService users(
      PasswordEncoder encoder,
      @Value("${app.oauth.admin-user}") String admin,
      @Value("${app.oauth.admin-password}") String ap,
      @Value("${app.oauth.reader-user}") String reader,
      @Value("${app.oauth.reader-password}") String rp) {
    return new InMemoryUserDetailsManager(
        User.withUsername(admin).password(encoder.encode(ap)).roles("ADMIN").build(),
        User.withUsername(reader).password(encoder.encode(rp)).roles("LECTURA").build());
  }

  @Bean
  RegisteredClientRepository clients(
      PasswordEncoder encoder,
      @Value("${app.oauth.admin-client-secret}") String adminSecret,
      @Value("${app.oauth.reader-client-secret}") String readerSecret) {
    var tokens = TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(15)).build();
    var admin =
        RegisteredClient.withId("banco-admin-id")
            .clientId("banco-admin")
            .clientSecret(encoder.encode(adminSecret))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope("cuentas.read")
            .scope("reportes.write")
            .scope("banco.admin")
            .tokenSettings(tokens)
            .build();
    var reader =
        RegisteredClient.withId("banco-lectura-id")
            .clientId("banco-lectura")
            .clientSecret(encoder.encode(readerSecret))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope("cuentas.read")
            .tokenSettings(tokens)
            .build();
    var web =
        RegisteredClient.withId("banco-web-id")
            .clientId("banco-web")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://127.0.0.1:8090/callback")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope("cuentas.read")
            .tokenSettings(tokens)
            .clientSettings(
                ClientSettings.builder()
                    .requireProofKey(true)
                    .requireAuthorizationConsent(true)
                    .build())
            .build();
    return new InMemoryRegisteredClientRepository(admin, reader, web);
  }

  @Bean
  OAuth2TokenCustomizer<JwtEncodingContext> claims(
      @Value("${app.oauth.audience}") String audience) {
    return context -> {
      if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
        List<String> roles;
        if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType()))
          roles =
              List.of(
                  context.getRegisteredClient().getClientId().equals("banco-admin")
                      ? "ADMIN"
                      : "LECTURA");
        else
          roles =
              context.getPrincipal().getAuthorities().stream()
                  .map(a -> a.getAuthority().replace("ROLE_", ""))
                  .toList();
        context.getClaims().audience(List.of(audience)).claim("roles", roles);
      }
    };
  }

  @Bean
  AuthorizationServerSettings settings(@Value("${app.oauth.issuer}") String issuer) {
    return AuthorizationServerSettings.builder().issuer(issuer).build();
  }

  // Una sola instancia escribe la clave; el volumen Docker mantiene la firma tras reinicios.
  @Bean
  JWKSource<SecurityContext> jwkSource(@Value("${app.oauth.key-file}") String keyFile)
      throws Exception {
    Path file = Path.of(keyFile).toAbsolutePath();
    Files.createDirectories(file.getParent());
    RSAKey key;
    if (Files.exists(file)) key = RSAKey.parse(Files.readString(file));
    else {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      var pair = generator.generateKeyPair();
      key =
          new RSAKey.Builder((RSAPublicKey) pair.getPublic())
              .privateKey((RSAPrivateKey) pair.getPrivate())
              .keyID(UUID.randomUUID().toString())
              .build();
      Path temp = Files.createTempFile(file.getParent(), "auth-key-", ".tmp");
      try {
        Files.writeString(temp, key.toJSONString());
        try {
          Files.setPosixFilePermissions(
              temp, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
        }
        Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE);
      } finally {
        Files.deleteIfExists(temp);
      }
    }
    if (!key.isPrivate())
      throw new IllegalStateException(
          "La clave del Authorization Server no contiene su parte privada");
    return new ImmutableJWKSet<>(new JWKSet(key));
  }

  @Bean
  JwtDecoder jwtDecoder(JWKSource<SecurityContext> source) {
    return OAuth2AuthorizationServerConfiguration.jwtDecoder(source);
  }
}
