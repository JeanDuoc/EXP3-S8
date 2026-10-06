package cl.duoc.cuentasservice;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cl.duoc.cuentasservice.config.*;
import cl.duoc.cuentasservice.eventos.ReporteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.config.name=test-application",
      "spring.cloud.config.enabled=false",
      "eureka.client.enabled=false",
      "spring.cloud.discovery.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:banktest8;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.sql.init.mode=always",
      "app.config-source=pruebas",
      "resilience4j.circuitbreaker.instances.banco.sliding-window-size=4",
      "resilience4j.circuitbreaker.instances.banco.minimum-number-of-calls=4",
      "resilience4j.circuitbreaker.instances.banco.failure-rate-threshold=50",
      "resilience4j.circuitbreaker.instances.banco.wait-duration-in-open-state=300ms",
      "resilience4j.circuitbreaker.instances.banco.permitted-number-of-calls-in-half-open-state=2"
    })
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(BancoIntegrationTest.TestJwt.class)
class BancoIntegrationTest {
  @TestConfiguration
  static class TestJwt {
    private final RSAKey key;

    TestJwt() throws Exception {
      var gen = KeyPairGenerator.getInstance("RSA");
      gen.initialize(2048);
      var pair = gen.generateKeyPair();
      key =
          new RSAKey.Builder((RSAPublicKey) pair.getPublic())
              .privateKey((RSAPrivateKey) pair.getPrivate())
              .keyID("pruebas")
              .build();
    }

    @Bean
    JwtEncoder encoder() {
      return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean
    @Primary
    JwtDecoder testDecoder() throws Exception {
      var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
      OAuth2TokenValidator<Jwt> aud =
          jwt ->
              jwt.getAudience().contains("banco-api")
                  ? OAuth2TokenValidatorResult.success()
                  : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
      decoder.setJwtValidator(
          new DelegatingOAuth2TokenValidator<>(
              JwtValidators.createDefaultWithIssuer("http://localhost:9000"), aud));
      return decoder;
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired FalloSimulado fallo;
  @Autowired CircuitBreakerRegistry registry;
  @Autowired JdbcTemplate jdbc;
  @Autowired MigracionImportador importador;
  @Autowired JwtEncoder encoder;
  @Autowired ReporteService reportes;

  @BeforeEach
  void reset() {
    fallo.activar(false);
    registry.circuitBreaker("banco").reset();
  }

  String token(String role, String scope, String issuer, String audience, Instant expiration) {
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject("cliente-prueba")
            .audience(List.of(audience))
            .issuedAt(Instant.now().minusSeconds(500))
            .expiresAt(expiration)
            .claim("roles", List.of(role))
            .claim("scope", scope)
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
        .getTokenValue();
  }

  String token(String role, String scope) {
    return token(role, scope, "http://localhost:9000", "banco-api", Instant.now().plusSeconds(900));
  }

  String admin() {
    return token("ADMIN", "cuentas.read reportes.write banco.admin");
  }

  @Test
  void apiExigeTokenOAuth() throws Exception {
    mvc.perform(get("/api/cuentas")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/cuentas").header("Authorization", "Bearer no-es-jwt"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void consultaLosDatosMigradosYLosIntereses() throws Exception {
    String t = admin();
    mvc.perform(get("/api/cuentas/106").header("Authorization", "Bearer " + t))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.interesCalculado").value(240.0))
        .andExpect(jsonPath("$.saldoFinal").value(12240.0));
    mvc.perform(get("/api/transacciones/1").header("Authorization", "Bearer " + t))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fecha").value("2024-06-30"));
    mvc.perform(get("/api/migracion").header("Authorization", "Bearer " + t))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseDeDatos.cuentas").value(50))
        .andExpect(jsonPath("$.baseDeDatos.transacciones").value(387))
        .andExpect(jsonPath("$.baseDeDatos.movimientosAnuales").value(524));
  }

  @Test
  void importacionIdempotente() throws Exception {
    long antes = jdbc.queryForObject("SELECT COUNT(*) FROM transacciones", Long.class);
    importador.importar();
    assertEquals(antes, jdbc.queryForObject("SELECT COUNT(*) FROM transacciones", Long.class));
    assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM migracion_lote", Long.class));
  }

  @Test
  void rolesYScopesLimitanElAcceso() throws Exception {
    String t = token("LECTURA", "cuentas.read");
    mvc.perform(get("/api/cuentas").header("Authorization", "Bearer " + t))
        .andExpect(status().isOk());
    mvc.perform(post("/api/reportes/solicitudes").header("Authorization", "Bearer " + t))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/demo/fallo")
                .header("Authorization", "Bearer " + t)
                .contentType("application/json")
                .content("{\"activo\":true}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/admin/circuito")
                .header("Authorization", "Bearer " + token("LECTURA", "cuentas.read banco.admin")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/cuentas").header("Authorization", "Bearer " + token("ADMIN", "openid")))
        .andExpect(status().isForbidden());
  }

  @Test
  void circuitoAbreYSeRecupera() throws Exception {
    String t = admin();
    fallo.activar(true);
    for (int i = 0; i < 4; i++)
      mvc.perform(get("/api/cuentas").header("Authorization", "Bearer " + t))
          .andExpect(status().isServiceUnavailable());
    assertEquals("OPEN", registry.circuitBreaker("banco").getState().name());
    fallo.activar(false);
    Thread.sleep(450);
    for (int i = 0; i < 2; i++)
      mvc.perform(get("/api/cuentas").header("Authorization", "Bearer " + t))
          .andExpect(status().isOk());
    assertEquals("CLOSED", registry.circuitBreaker("banco").getState().name());
  }

  @Test
  void ausenciaDeRegistroNoAbreCircuitoYSeValidanParametros() throws Exception {
    String t = admin();
    for (int i = 0; i < 4; i++)
      mvc.perform(get("/api/cuentas/999999").header("Authorization", "Bearer " + t))
          .andExpect(status().isNotFound());
    assertEquals(0, registry.circuitBreaker("banco").getMetrics().getNumberOfFailedCalls());
    mvc.perform(get("/api/transacciones?limite=0").header("Authorization", "Bearer " + t))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/transacciones?limite=2").header("Authorization", "Bearer " + t))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void validaEmisorAudienciaYVencimiento() throws Exception {
    var vencido =
        token(
            "ADMIN",
            "cuentas.read",
            "http://localhost:9000",
            "banco-api",
            Instant.now().minusSeconds(120));
    var emisor =
        token(
            "ADMIN",
            "cuentas.read",
            "http://otro-banco",
            "banco-api",
            Instant.now().plusSeconds(100));
    var audiencia =
        token(
            "ADMIN",
            "cuentas.read",
            "http://localhost:9000",
            "otra-api",
            Instant.now().plusSeconds(100));
    for (String t : List.of(vencido, emisor, audiencia))
      mvc.perform(get("/api/cuentas").header("Authorization", "Bearer " + t))
          .andExpect(status().isUnauthorized());
  }

  @Test
  void reporteSeGeneraPorJmsYUnDuplicadoNoSeReprocesa() throws Exception {
    String t = admin();
    var response =
        mvc.perform(post("/api/reportes/solicitudes").header("Authorization", "Bearer " + t))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.estado").value("PENDIENTE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = mapper.readTree(response).get("id").asText();
    long fin = System.currentTimeMillis() + 10000;
    while (System.currentTimeMillis() < fin && !reportes.buscar(id).estado().equals("COMPLETADO"))
      Thread.sleep(100);
    var antes = reportes.buscar(id);
    assertEquals("COMPLETADO", antes.estado());
    assertEquals(50, antes.resultado().get("cuentas").asInt());
    reportes.completar(id);
    assertEquals(antes.completadoEn(), reportes.buscar(id).completadoEn());
    assertFalse(
        jdbc.queryForObject("SELECT pendiente FROM eventos_outbox WHERE id=?", Boolean.class, id));
  }
}
