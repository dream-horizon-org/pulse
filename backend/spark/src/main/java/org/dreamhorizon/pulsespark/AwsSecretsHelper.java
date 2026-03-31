package org.dreamhorizon.pulsespark;

import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches job connection parameters from AWS Secrets Manager.
 *
 * <p>The secret (e.g. {@code prod/pulseserver/appenv}) must be a JSON object.
 * Known keys are mapped to Spark job param names so the caller does not need
 * to pass --mysql_host, --mysql_password, etc. as CLI arguments.
 *
 * <p>CLI arguments always take precedence over secret values.
 */
public class AwsSecretsHelper {

    private static final Logger log = LoggerFactory.getLogger(AwsSecretsHelper.class);

    private static final Map<String, String> SECRET_TO_JOB_KEY = Map.ofEntries(
            Map.entry("MYSQL_WRITER_HOST",  "mysql_host"),
            Map.entry("MYSQL_DATABASE",     "mysql_db"),
            Map.entry("MYSQL_USER",         "mysql_user"),
            Map.entry("MYSQL_PASSWORD",     "mysql_password"),
            Map.entry("CLICKHOUSE_HOST",    "clickhouse_host"),
            Map.entry("CLICKHOUSE_PORT",    "clickhouse_port"),
            Map.entry("CLICKHOUSE_USERNAME","clickhouse_user"),
            Map.entry("CLICKHOUSE_PASSWORD","clickhouse_password")
    );

    /**
     * Fetches the secret and returns a param map (lowercase job-arg keys → values).
     *
     * @param secretName secret name or ARN, e.g. {@code prod/pulseserver/appenv}
     * @param region     AWS region, e.g. {@code ap-south-1}
     */
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

            // Support two secret formats:
            // 1. Flat JSON:  {"MYSQL_WRITER_HOST": "host", ...}
            // 2. Array format: {"app_env": [{"key": "MYSQL_WRITER_HOST", "value": "host"}, ...]}
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
            log.info("Resolved secret values: {}", result);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load secret '" + secretName + "': " + e.getMessage(), e);
        }
    }

    /**
     * Merges secret values into {@code params} without overwriting keys already present.
     * Fetches from Secrets Manager only if {@code --secrets_name} is set in {@code params}.
     */
    public static void mergeInto(Map<String, String> params) {
        var secretName = params.get("secrets_name");
        if (secretName == null || secretName.isBlank()) return;
        var region = params.getOrDefault("aws_region", "ap-south-1");
        var secretParams = load(secretName, region);
        secretParams.forEach(params::putIfAbsent);
    }
}
