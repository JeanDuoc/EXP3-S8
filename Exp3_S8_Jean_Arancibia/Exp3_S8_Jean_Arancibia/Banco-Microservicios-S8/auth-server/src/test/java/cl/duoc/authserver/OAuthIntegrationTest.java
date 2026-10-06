package cl.duoc.authserver;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OAuthIntegrationTest {
  static final java.nio.file.Path KEYS;

  static {
    try {
      KEYS = java.nio.file.Files.createTempDirectory("banco-oauth-test-").resolve("key.json");
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("app.oauth.key-file", () -> KEYS.toString());
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JwtDecoder decoder;

  @Test
  void clientCredentialsEntregaTokenConScopesRolesYAudiencia() throws Exception {
    var body =
        mvc.perform(
                post("/oauth2/token")
                    .with(httpBasic("banco-admin", "AdminClientLocal2026!"))
                    .contentType("application/x-www-form-urlencoded")
                    .param("grant_type", "client_credentials")
                    .param("scope", "cuentas.read reportes.write banco.admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    var jwt = decoder.decode(mapper.readTree(body).get("access_token").asText());
    assertEquals("http://localhost:9000", jwt.getIssuer().toString());
    assertTrue(jwt.getAudience().contains("banco-api"));
    assertTrue(jwt.getClaimAsStringList("roles").contains("ADMIN"));
  }

  @Test
  void rechazaClienteConSecretoIncorrecto() throws Exception {
    mvc.perform(
            post("/oauth2/token")
                .with(httpBasic("banco-admin", "incorrecto"))
                .contentType("application/x-www-form-urlencoded")
                .param("grant_type", "client_credentials"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void clienteLecturaNoPuedeSolicitarScopesAdministrativos() throws Exception {
    mvc.perform(
            post("/oauth2/token")
                .with(httpBasic("banco-lectura", "ReaderClientLocal2026!"))
                .contentType("application/x-www-form-urlencoded")
                .param("grant_type", "client_credentials")
                .param("scope", "banco.admin"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_scope"));
  }

  @Test
  void metadataYJwksSonPublicosYNoExponenLaClavePrivada() throws Exception {
    mvc.perform(get("/.well-known/openid-configuration"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.issuer").value("http://localhost:9000"));
    mvc.perform(get("/oauth2/jwks"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
        .andExpect(jsonPath("$.keys[0].d").doesNotExist());
  }
}
