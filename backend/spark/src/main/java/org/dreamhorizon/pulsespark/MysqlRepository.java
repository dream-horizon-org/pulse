package org.dreamhorizon.pulsespark;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dreamhorizon.pulsespark.model.FunnelDefinition;
import org.dreamhorizon.pulsespark.model.FunnelFilter;
import org.dreamhorizon.pulsespark.model.FunnelStep;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MysqlRepository {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<FunnelStep>>   STEPS_TYPE   = new TypeReference<>() {};
    private static final TypeReference<List<FunnelFilter>> FILTERS_TYPE = new TypeReference<>() {};

    private final String jdbcUrl;
    private final String user;
    private final String password;

    public MysqlRepository(String host, int port, String db, String user, String password) {
        this.jdbcUrl  = "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true"
                .formatted(host, port, db);
        this.user     = user;
        this.password = password;
    }

    /** Fetches all funnels, or a single funnel if {@code funnelId} is non-null. */
    public List<FunnelDefinition> fetchFunnels(String funnelId) throws Exception {
        var sql = funnelId != null
                ? "SELECT * FROM funnel WHERE funnel_id = ?"
                : "SELECT * FROM funnel";

        var results = new ArrayList<FunnelDefinition>();
        try (var conn = DriverManager.getConnection(jdbcUrl, user, password);
             var stmt = conn.prepareStatement(sql)) {

            if (funnelId != null) stmt.setString(1, funnelId);
            var rs = stmt.executeQuery();

            while (rs.next()) {
                List<FunnelStep>   steps   = MAPPER.readValue(rs.getString("steps_json"), STEPS_TYPE);
                List<FunnelFilter> filters = rs.getString("filters_json") != null
                        ? MAPPER.readValue(rs.getString("filters_json"), FILTERS_TYPE)
                        : List.of();

                results.add(new FunnelDefinition(
                        rs.getString("funnel_id"),
                        rs.getString("project_id"),
                        steps,
                        rs.getLong("window_seconds"),
                        rs.getString("mode"),
                        rs.getInt("date_range_days"),
                        filters
                ));
            }
        }
        return results;
    }

    /** Returns all distinct project IDs from the {@code projects} table. */
    public List<String> fetchProjectIds() throws SQLException {
        var ids = new ArrayList<String>();
        try (var conn = DriverManager.getConnection(jdbcUrl, user, password);
             var stmt = conn.createStatement();
             var rs   = stmt.executeQuery("SELECT DISTINCT project_id FROM projects")) {
            while (rs.next()) ids.add(rs.getString(1));
        }
        return ids;
    }
}
