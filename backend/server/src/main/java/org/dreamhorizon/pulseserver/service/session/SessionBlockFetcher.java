package org.dreamhorizon.pulseserver.service.session;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.service.configs.IS3BucketClient;
import org.dreamhorizon.pulseserver.service.session.models.BlockCoordinates;
import org.xerial.snappy.Snappy;

@Slf4j
public class SessionBlockFetcher {

  private static final Pattern RANGE_PATTERN = Pattern.compile("^range=bytes=(\\d+)-(\\d+)$");
  private static final int MAX_DECOMPRESSED_SIZE = 200 * 1024 * 1024; // 200MB safety limit

  private final IS3BucketClient s3BucketClient;

  @Inject
  public SessionBlockFetcher(IS3BucketClient s3BucketClient) {
    this.s3BucketClient = s3BucketClient;
  }

  /**
   * Fetches multiple blocks from S3 in parallel, decompresses Snappy, and returns concatenated JSONL.
   *
   * @param blockUrls list of block URLs in format s3://bucket/key?range=bytes=start-end
   * @return concatenated JSONL bytes
   */
  public Single<byte[]> fetchBlocks(List<String> blockUrls) {
    List<Single<byte[]>> fetches = blockUrls.stream()
        .map(this::fetchSingleBlock)
        .collect(Collectors.toList());

    return Single.zip(fetches, this::assembleDecompressedJsonl);
  }

  private Single<byte[]> fetchSingleBlock(String blockUrl) {
    BlockCoordinates coords = parseBlockUrl(blockUrl);
    return s3BucketClient.getObjectRange(coords.getBucket(), coords.getKey(), coords.getStartByte(), coords.getEndByte())
        .doOnError(err -> log.error("Failed to fetch block from S3: {} - {}", blockUrl, err.getMessage()));
  }

  private byte[] assembleDecompressedJsonl(Object[] compressedBlocks) {
    StringBuilder jsonl = new StringBuilder();
    for (Object block : compressedBlocks) {
      byte[] compressed = (byte[]) block;
      byte[] decompressed = decompressSnappy(compressed);
      String text = new String(decompressed, StandardCharsets.UTF_8);
      jsonl.append(text);
      if (!text.endsWith("\n")) {
        jsonl.append("\n");
      }
    }
    String result = jsonl.toString().stripTrailing();
    return result.getBytes(StandardCharsets.UTF_8);
  }

  private byte[] decompressSnappy(byte[] compressed) {
    try {
      int decompressedSize = Snappy.uncompressedLength(compressed);
      if (decompressedSize <= 0 || decompressedSize > MAX_DECOMPRESSED_SIZE) {
        throw ServiceError.INTERNAL_SERVER_ERROR.getCustomException(
            String.format("Snappy block size out of range: %d (max %d)", decompressedSize, MAX_DECOMPRESSED_SIZE));
      }
      return Snappy.uncompress(compressed);
    } catch (IOException e) {
      throw ServiceError.INTERNAL_SERVER_ERROR.getCustomException(
          "Failed to decompress Snappy block: " + e.getMessage());
    }
  }

  private BlockCoordinates parseBlockUrl(String blockUrl) {
    URI uri = URI.create(blockUrl);
    String bucket = uri.getHost();
    String key = uri.getPath().substring(1); // strip leading /
    String query = uri.getQuery();

    Matcher matcher = RANGE_PATTERN.matcher(query);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid block URL range format: " + blockUrl);
    }

    long startByte = Long.parseLong(matcher.group(1));
    long endByte = Long.parseLong(matcher.group(2));

    return BlockCoordinates.builder()
        .bucket(bucket)
        .key(key)
        .startByte(startByte)
        .endByte(endByte)
        .build();
  }
}
