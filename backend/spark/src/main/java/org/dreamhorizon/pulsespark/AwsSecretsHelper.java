package org.dreamhorizon.pulsespark;

import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AwsSecretsHelper {

    private static final Logger log = LoggerFactory.getLogger(AwsSecretsHelper.class);

    private static final Map<String, String> SECRET_TO_JOB_KEY = Map.ofEntries(
            Map.entry("MYSQL_WRITER_HOST",   "mysql_host"),
            Map.entry("MYSQL_DATABASE",      "mysql_db"),
            Map.entry("MYSQL_USER",          "mysql_user"),
            Map.entry("MYSQL_PASSWORD",      "mysql_password"),
            Map.entry("CLICKHOUSE_HOST",     "clickhouse_host"),
            Map.entry("CLICKHOUSE_PORT",     "clickhouse_port"),
            Map.entry("CLICKHOUSE_USERNAME", "clickhouse_user"),
            Map.entry("CLICKHOUSE_PASSWORD", "clickhouse_password")
    );

    @SuppressWarnings("unchecked")
    public static Map<String, String> load(String secretName, String region) {
        log.info("Loading job params from Secrets Manager: {} (region={})", secretName, region);
        try {
            var client = AWSSecretsManagerClientBuilder.standard()
                    .withRegion(region)
                    .build();
            var secretString = client.getSecretValue(
                    new GetSecretValueRequest().withSecretId(secretName)
            ).getSecretString();

            var mapper = new ObjectMapper();
            Map<String, Object> root = mapper.readValue(secretString, Map.class);
            var result = new HashMap<String, String>();

            if (root.containsKey("app_env")) {
                var entries = (List<Map<String, Object>>) root.get("app_env");
                for (var entry : entries) {
                    var secretKey = String.valueOf(entry.get("key"));
                    var jobKey    = SECRET_TO_JOB_KEY.get(secretKey);
                    if (jobKey != null && entry.get("value") != null) {
                        result.put(jobKey, String.valueOf(entry.get("value")));
                    }
                }
            } else {
                for (var entry : root.entrySet()) {
                    var jobKey = SECRET_TO_JOB_KEY.get(entry.getKey());
                    if (jobKey != null && entry.getValue() != null) {
                        result.put(jobKey, entry.getValue().toString());
                    }
                }
            }
            log.info("Resolved {} param(s) from secret: {}", result.size(), result.keySet());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load secret '" + secretName + "': " + e.getMessage(), e);
        }
    }

    public static void mergeInto(Map<String, String> params) {
        var secretName = params.get("secrets_name");
        if (secretName == null || secretName.isBlank()) return;
        var region = params.getOrDefault("aws_region", "ap-south-1");
        load(secretName, region).forEach(params::putIfAbsent);
    }
}
