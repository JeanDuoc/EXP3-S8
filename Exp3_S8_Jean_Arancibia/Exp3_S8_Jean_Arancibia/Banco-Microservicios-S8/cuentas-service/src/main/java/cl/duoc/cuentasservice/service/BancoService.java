package cl.duoc.cuentasservice.service;

import cl.duoc.cuentasservice.exception.*;
import cl.duoc.cuentasservice.model.*;
import cl.duoc.cuentasservice.repository.BancoRepository;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

@Service
public class BancoService {
  private final BancoRepository repo;
  private final CircuitBreakerFactory<?, ?> breakers;

  public BancoService(BancoRepository repo, CircuitBreakerFactory<?, ?> breakers) {
    this.repo = repo;
    this.breakers = breakers;
  }

  private <T> T ejecutar(Supplier<T> consulta) {
    return breakers
        .create("banco")
        .run(
            consulta,
            e -> {
              throw new ServicioNoDisponibleException(e);
            });
  }

  public List<Cuenta> cuentas() {
    return ejecutar(repo::cuentas);
  }

  // La ausencia de un registro es una respuesta normal, no un fallo del circuito.
  public Cuenta cuenta(long id) {
    return ejecutar(() -> repo.cuenta(id))
        .orElseThrow(() -> new NoEncontradoException("Cuenta no encontrada: " + id));
  }

  public List<Transaccion> transacciones(int limite, int offset) {
    return ejecutar(() -> repo.transacciones(limite, offset));
  }

  public Transaccion transaccion(long id) {
    return ejecutar(() -> repo.transaccion(id))
        .orElseThrow(() -> new NoEncontradoException("Transacción no encontrada: " + id));
  }

  public List<MovimientoAnual> anuales(Long id, int limite, int offset) {
    return ejecutar(() -> repo.anuales(id, limite, offset));
  }

  public Map<String, Long> conteos() {
    return ejecutar(repo::conteos);
  }
}
