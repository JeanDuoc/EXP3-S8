package cl.duoc.cuentasservice.eventos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.*;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class ReporteConsumer {
  private final ReporteService reportes;
  private final ObjectMapper mapper;
  private static final Logger log = LoggerFactory.getLogger(ReporteConsumer.class);

  public ReporteConsumer(ReporteService reportes, ObjectMapper mapper) {
    this.reportes = reportes;
    this.mapper = mapper;
  }

  @JmsListener(destination = "${app.jms.queue}")
  public void recibir(String mensaje) throws Exception {
    var event = mapper.readTree(mensaje);
    if (!"REPORTE_SOLICITADO".equals(event.path("tipo").asText()))
      throw new IllegalArgumentException("Tipo de evento desconocido");
    var id = event.path("solicitudId").asText();
    java.util.UUID.fromString(id);
    reportes.completar(id);
    log.info("JMS_CONSUMIDO solicitud={} estado=COMPLETADO", id);
  }
}
