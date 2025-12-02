package fi.csc.avaa.paituli.db;

import java.util.Map;
import org.testcontainers.containers.PostgreSQLContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Override
    public Map<String, String> start() {
        postgres.start();
        return Map.of(
            "quarkus.datasource.jdbc.url", postgres.getJdbcUrl()
        );
    }

    @Override
    public void stop() {
        postgres.stop();
    }
}
