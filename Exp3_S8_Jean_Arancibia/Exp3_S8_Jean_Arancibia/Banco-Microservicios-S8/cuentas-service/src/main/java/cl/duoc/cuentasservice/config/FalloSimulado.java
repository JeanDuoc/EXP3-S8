package cl.duoc.cuentasservice.config;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class FalloSimulado {
  private final AtomicBoolean activo = new AtomicBoolean(false);

  public boolean activo() {
    return activo.get();
  }

  public void activar(boolean value) {
    activo.set(value);
  }

  public void verificar() {
    if (activo())
      throw new IllegalStateException("Fallo de datos simulado para demostrar resiliencia");
  }
}
