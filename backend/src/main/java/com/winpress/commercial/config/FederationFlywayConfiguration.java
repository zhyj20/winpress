package com.winpress.commercial.config;

import javax.sql.DataSource;
import jakarta.annotation.PostConstruct;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Configuration;

/**
 * A dedicated history for the GEO federation adapter.  The mature WinPress business schema keeps
 * its existing initialization path; this migration is opt-in and safe for an isolated database.
 */
@Configuration
public class FederationFlywayConfiguration {
  private final DataSource dataSource;
  private final WinPressProperties properties;

  public FederationFlywayConfiguration(DataSource dataSource, WinPressProperties properties) {
    this.dataSource = dataSource;
    this.properties = properties;
  }

  /**
   * Must run while the application context is being assembled. An ApplicationRunner is too late:
   * scheduled reconciliation can otherwise query the adapter schema before it exists.
   */
  @PostConstruct
  void migrateFederationSchemaBeforeSchedulersStart() {
    WinPressProperties.Federation federation = properties.getFederation();
    if (!federation.isEnabled() || !federation.isMigrateOnStart()) return;
    Flyway.configure()
        .dataSource(dataSource)
        .schemas("winpress_federation")
        .defaultSchema("winpress_federation")
        .table("flyway_schema_history")
        .locations("classpath:db/federation-migration")
        .createSchemas(true)
        .load()
        .migrate();
  }
}
