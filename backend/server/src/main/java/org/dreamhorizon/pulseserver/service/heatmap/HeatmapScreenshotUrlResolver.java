package org.dreamhorizon.pulseserver.service.heatmap;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.config.HeatmapS3Config;
import org.dreamhorizon.pulseserver.config.SessionReplayS3Config;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * Lists heatmap screenshot objects in S3 (same key layout as heatmap-screenshot-ingestion) and
 * returns browser-ready URLs (public base URL or presigned GET).
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class HeatmapScreenshotUrlResolver {

  private static final int MAX_SEGMENT_LEN = 200;
  private static final int MAX_OBJECTS_TOTAL = 20;
  private static final int MAX_OBJECTS_PER_DAY = 5;
  private static final Duration PRESIGN_TTL = Duration.ofMinutes(15);
  private static final DateTimeFormatter DAY_FOLDER = DateTimeFormatter.BASIC_ISO_DATE;
  private static final Pattern CTRL_CHARS = Pattern.compile("[\\x00-\\x1f\\x7f]");

  private static final String DEFAULT_PREFIX = "heatmap-screenshots";

  private final S3AsyncClient s3Client;
  private final ApplicationConfig applicationConfig;

  private volatile S3Presigner presigner;
  private volatile S3AsyncClient heatmapDedicatedListClient;

  /**
   * @param dateFrom inclusive ISO local date (UTC calendar day), e.g. {@code 2026-04-08}
   * @param dateTo inclusive ISO local date (UTC calendar day)
   */
  public List<String> resolveForScreen(
      String projectId,
      String screenName,
      String dateFrom,
      String dateTo,
      String appVersion,
      String platform,
      String breakpoint) {
    HeatmapS3Config hm = applicationConfig.getHeatmapS3();
    SessionReplayS3Config sr = applicationConfig.getSessionReplayS3();

    String bucket = resolveBucket(hm, sr);
    if (bucket == null) {
      return Collections.emptyList();
    }
    if (StringUtils.isBlank(appVersion)) {
      return Collections.emptyList();
    }

    LocalDate start;
    LocalDate end;
    try {
      start = LocalDate.parse(dateFrom);
      end = LocalDate.parse(dateTo);
    } catch (Exception e) {
      log.warn("Invalid heatmap screenshot date range: {} .. {}", dateFrom, dateTo);
      return Collections.emptyList();
    }

    LocalDate newest = end.isBefore(start) ? start : end;
    LocalDate oldest = end.isBefore(start) ? end : start;

    String rootPrefix = normalizedRootPrefix();
    List<String> keys = new ArrayList<>();
    for (LocalDate d = newest; !d.isBefore(oldest) && keys.size() < MAX_OBJECTS_TOTAL; d = d.minusDays(1)) {
      String prefix = buildListPrefix(rootPrefix, projectId, d, screenName, platform, appVersion, breakpoint);
      List<S3Object> dayObjects = listJsonObjectsUnderPrefix(hm, sr, bucket, prefix);
      dayObjects.sort(Comparator.comparing(S3Object::lastModified, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
      int n = Math.min(MAX_OBJECTS_PER_DAY, MAX_OBJECTS_TOTAL - keys.size());
      for (int i = 0; i < Math.min(n, dayObjects.size()); i++) {
        String key = dayObjects.get(i).key();
        if (StringUtils.isNotBlank(key) && !key.endsWith("/")) {
          keys.add(key);
        }
      }
    }

    List<String> urls = new ArrayList<>(keys.size());
    for (String key : keys) {
      try {
        urls.add(toUrl(bucket, key));
      } catch (Exception e) {
        log.warn("Failed to build URL for s3://{}/{}: {}", bucket, key, e.getMessage());
      }
    }
    return urls;
  }

  private static String resolveBucket(HeatmapS3Config hm, SessionReplayS3Config sr) {
    if (hm != null && StringUtils.isNotBlank(hm.getBucket())) {
      return hm.getBucket().trim();
    }
    if (sr != null && StringUtils.isNotBlank(sr.getBucket())) {
      return sr.getBucket().trim();
    }
    return null;
  }

  /**
   * Endpoint/region/credentials for heatmap S3 when a dedicated bucket is set; falls back to session
   * replay for fields omitted (typical single MinIO).
   */
  private static SessionReplayS3Config mergedHeatmapConnection(HeatmapS3Config hm, SessionReplayS3Config sr) {
    String ep = StringUtils.defaultIfBlank(hm.getEndpoint(), sr != null ? sr.getEndpoint() : null);
    String reg = StringUtils.defaultIfBlank(hm.getRegion(), sr != null ? sr.getRegion() : null);
    String ak = StringUtils.defaultIfBlank(hm.getAccessKeyId(), sr != null ? sr.getAccessKeyId() : null);
    String sk = StringUtils.defaultIfBlank(hm.getSecretAccessKey(), sr != null ? sr.getSecretAccessKey() : null);
    return new SessionReplayS3Config(hm.getBucket(), ep, reg, ak, sk);
  }

  private S3AsyncClient listClient(HeatmapS3Config hm, SessionReplayS3Config sr) {
    if (hm == null || StringUtils.isBlank(hm.getBucket())) {
      return s3Client;
    }
    SessionReplayS3Config merged = mergedHeatmapConnection(hm, sr);
    if (StringUtils.isBlank(merged.getEndpoint())) {
      return s3Client;
    }
    if (sr != null && StringUtils.isNotBlank(sr.getEndpoint())) {
      if (normalizeEndpoint(merged.getEndpoint()).equals(normalizeEndpoint(sr.getEndpoint()))) {
        return s3Client;
      }
    }
    return dedicatedHeatmapListClient(merged);
  }

  private static String normalizeEndpoint(String endpoint) {
    if (endpoint == null) {
      return "";
    }
    return StringUtils.stripEnd(endpoint.trim(), "/");
  }

  private S3AsyncClient dedicatedHeatmapListClient(SessionReplayS3Config merged) {
    if (heatmapDedicatedListClient != null) {
      return heatmapDedicatedListClient;
    }
    synchronized (this) {
      if (heatmapDedicatedListClient == null) {
        heatmapDedicatedListClient = buildAsyncClient(merged);
      }
      return heatmapDedicatedListClient;
    }
  }

  private static S3AsyncClient buildAsyncClient(SessionReplayS3Config conn) {
    String region = StringUtils.defaultIfBlank(conn.getRegion(), "ap-south-1");
    String accessKey = StringUtils.defaultString(conn.getAccessKeyId());
    String secretKey = StringUtils.defaultString(conn.getSecretAccessKey());
    return S3AsyncClient.builder()
        .httpClientBuilder(NettyNioAsyncHttpClient.builder())
        .region(Region.of(region))
        .endpointOverride(URI.create(conn.getEndpoint().trim()))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
        .forcePathStyle(true)
        .build();
  }

  static String sanitizePathSegment(String raw, String fallback) {
    if (raw == null) {
      raw = "";
    }
    String trimmed = raw.trim();
    if (trimmed.length() > MAX_SEGMENT_LEN) {
      trimmed = trimmed.substring(0, MAX_SEGMENT_LEN);
    }
    String cleaned =
        CTRL_CHARS
            .matcher(trimmed.replace('\\', '_').replace('/', '_'))
            .replaceAll("")
            .replaceFirst("^\\.+", "");
    return cleaned.isEmpty() ? fallback : cleaned;
  }

  private String normalizedRootPrefix() {
    String p = applicationConfig.getHeatmapScreenshotsS3Prefix();
    if (StringUtils.isBlank(p)) {
      p = DEFAULT_PREFIX;
    }
    return StringUtils.stripEnd(p.trim(), "/");
  }

  private static String buildListPrefix(
      String rootPrefix,
      String projectId,
      LocalDate day,
      String screenName,
      String platform,
      String appVersion,
      String breakpoint) {
    String dayFolder = day.format(DAY_FOLDER);
    return String.join(
            "/",
            rootPrefix,
            sanitizePathSegment(projectId, "unknown"),
            dayFolder,
            sanitizePathSegment(screenName, "screen"),
            sanitizePathSegment(platform, "unknown"),
            sanitizePathSegment(appVersion, "unknown"),
            sanitizePathSegment(breakpoint, "unknown"))
        + "/";
  }

  private List<S3Object> listJsonObjectsUnderPrefix(
      HeatmapS3Config hm, SessionReplayS3Config sr, String bucket, String prefix) {
    S3AsyncClient client = listClient(hm, sr);
    List<S3Object> out = new ArrayList<>();
    String token = null;
    do {
      ListObjectsV2Request.Builder req = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).maxKeys(500);
      if (token != null) {
        req.continuationToken(token);
      }
      ListObjectsV2Response resp = client.listObjectsV2(req.build()).join();
      for (S3Object obj : resp.contents()) {
        String key = obj.key();
        if (key != null && key.endsWith(".json") && !key.endsWith("/")) {
          out.add(obj);
        }
      }
      token = Boolean.TRUE.equals(resp.isTruncated()) ? resp.nextContinuationToken() : null;
    } while (token != null);
    return out;
  }

  private String toUrl(String bucket, String key) {
    String pub = applicationConfig.getHeatmapScreenshotsPublicBaseUrl();
    if (StringUtils.isNotBlank(pub)) {
      return StringUtils.stripEnd(pub.trim(), "/") + "/" + key;
    }
    GetObjectPresignRequest presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(PRESIGN_TTL)
            .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
            .build();
    PresignedGetObjectRequest presigned = presigner().presignGetObject(presignRequest);
    return presigned.url().toString();
  }

  private S3Presigner presigner() {
    S3Presigner local = presigner;
    if (local != null) {
      return local;
    }
    synchronized (this) {
      if (presigner == null) {
        presigner = buildPresigner();
      }
      return presigner;
    }
  }

  private S3Presigner buildPresigner() {
    SessionReplayS3Config conn = resolvedPresignConnection();
    if (conn != null && StringUtils.isNotBlank(conn.getEndpoint())) {
      String region = StringUtils.defaultIfBlank(conn.getRegion(), "ap-south-1");
      String accessKey = StringUtils.defaultString(conn.getAccessKeyId());
      String secretKey = StringUtils.defaultString(conn.getSecretAccessKey());
      return S3Presigner.builder()
          .region(Region.of(region))
          .endpointOverride(URI.create(conn.getEndpoint().trim()))
          .credentialsProvider(
              StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
          .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
          .build();
    }
    return S3Presigner.builder()
        .region(Region.AP_SOUTH_1)
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }

  private SessionReplayS3Config resolvedPresignConnection() {
    HeatmapS3Config hm = applicationConfig.getHeatmapS3();
    SessionReplayS3Config sr = applicationConfig.getSessionReplayS3();
    if (hm != null && StringUtils.isNotBlank(hm.getBucket())) {
      return mergedHeatmapConnection(hm, sr);
    }
    return sr;
  }
}
