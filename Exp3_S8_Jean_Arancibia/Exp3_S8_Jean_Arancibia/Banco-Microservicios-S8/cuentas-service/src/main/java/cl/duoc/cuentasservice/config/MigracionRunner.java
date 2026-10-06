package cl.duoc.cuentasservice.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MigracionRunner implements ApplicationRunner {
  private final MigracionImportador importador;

  public MigracionRunner(MigracionImportador importador) {
    this.importador = importador;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    importador.importar();
  }
}
