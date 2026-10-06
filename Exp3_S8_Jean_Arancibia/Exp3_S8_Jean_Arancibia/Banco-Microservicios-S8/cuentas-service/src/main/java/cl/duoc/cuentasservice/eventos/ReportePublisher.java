package cl.duoc.cuentasservice.eventos;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReportePublisher {
  private final ReporteService reportes;
  private final JmsTemplate jms;
  private final ObjectMapper mapper;
  private final String queue;
  private static final Logger log = LoggerFactory.getLogger(ReportePublisher.class);

  public ReportePublisher(
      ReporteService reportes,
      JmsTemplate jms,
      ObjectMapper mapper,
      @Value("${app.jms.queue}") String queue) {
    this.reportes = reportes;
    this.jms = jms;
    this.mapper = mapper;
    this.queue = queue;
  }

  @Scheduled(fixedDelayString = "${app.eventos.publisher-delay-ms:2000}", initialDelay = 3000)
  public void publicar() {
    try {
      for (String id : reportes.pendientes()) {
        try {
          // La outbox permanece pendiente hasta que el consumidor confirme en la base.
          // Así también se recupera un mensaje si el broker local reinicia sin persistencia.
          jms.setExplicitQosEnabled(true);
          jms.setDeliveryPersistent(true);
          jms.convertAndSend(
              queue,
              mapper.writeValueAsString(Map.of("tipo", "REPORTE_SOLICITADO", "solicitudId", id)));
          reportes.registrarIntento(id);
          log.info("JMS_PUBLICADO solicitud={} cola={}", id, queue);
        } catch (Exception e) {
          log.warn("JMS_NO_DISPONIBLE solicitud={} permanece pendiente", id);
          break;
        }
      }
    } catch (Exception e) {
      log.warn("OUTBOX_NO_DISPONIBLE: se reintentará en el próximo ciclo");
    }
  }
}
