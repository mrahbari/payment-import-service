package com.example.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * After the app is up, print a short list of local URLs to stdout (plain text, not JSON logs) so
 * it is easy to find Swagger, health, and the import UI (served by import-service).
 */
@Component
@ConditionalOnWebApplication
public class StartupUrlBanner {

  private static final String RULE = "----------------------------------------------------------------";

  private final Environment environment;
  private final boolean printUrls;
  private final String importServiceBase;

  public StartupUrlBanner(
      Environment environment,
      @Value("${app.startup.print-urls:true}") boolean printUrls,
      @Value("${app.import-service.public-base-url:http://localhost:3000}") String importServiceBase) {
    this.environment = environment;
    this.printUrls = printUrls;
    String base = importServiceBase;
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    this.importServiceBase = base;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(0)
  public void printUrlBanner() {
    if (!printUrls) {
      return;
    }
    String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
    String base = "http://localhost:" + port;
    StringBuilder b = new StringBuilder();
    b.append("\n").append(RULE).append("\n");
    b.append("  payment-service is ready. Useful local URLs:\n");
    b.append("  - API / JSON home:  ").append(base).append("/\n");
    b.append("  - Health:           ").append(base).append("/actuator/health\n");
    b.append("  - Swagger UI:       ").append(base).append("/swagger-ui.html\n");
    b.append("  - OpenAPI spec:     ").append(base).append("/api-docs\n");
    b.append("  - Example contract: ")
        .append(base)
        .append("/api/v1/contracts/by-number/CNT-1001\n");
    b.append("  - Import in browser: ").append(importServiceBase).append("/payments/import\n");
    b.append("  - Import service:   ")
        .append(importServiceBase)
        .append("/\n");
    b.append("  - Start import (2nd terminal, from repo root): ./scripts/run-import-service.sh  OR  ")
        .append("cd import-service && npm run build && npm start\n");
    b.append("    (default PORT=3000; set PORT=3001 if 3000 is in use, then use that URL.)\n");
    b.append(RULE).append("\n");
    System.out.print(b);
    System.out.flush();
  }
}
