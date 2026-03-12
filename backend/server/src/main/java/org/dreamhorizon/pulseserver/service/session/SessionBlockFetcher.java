package org.dreamhorizon.pulseserver.service.session;

import com.github.luben.zstd.Zstd;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.service.configs.IS3BucketClient;

@Slf4j
public class SessionBlockFetcher {

  private static final Pattern RANGE_PATTERN = Pattern.compile("^range=bytes=(\\d+)-(\\d+)$");
  private static final int MAX_DECOMPRESSED_SIZE = 50 * 1024 * 1024; // 50MB safety limit

  private final IS3BucketClient s3BucketClient;

  @Inject
  public SessionBlockFetcher(IS3BucketClient s3BucketClient) {
    this.s3BucketClient = s3BucketClient;
  }

  /**
   * Fetches multiple blocks from S3 in parallel, decompresses zstd, and returns concatenated JSONL.
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
    return s3BucketClient.getObjectRange(coords.bucket, coords.key, coords.startByte, coords.endByte)
        .doOnError(err -> log.error("Failed to fetch block from S3: {} - {}", blockUrl, err.getMessage()));
  }

  private byte[] assembleDecompressedJsonl(Object[] compressedBlocks) {
    StringBuilder jsonl = new StringBuilder();
    for (Object block : compressedBlocks) {
      byte[] compressed = (byte[]) block;
      byte[] decompressed = decompressZstd(compressed);
      String text = new String(decompressed, StandardCharsets.UTF_8);
      jsonl.append(text);
      if (!text.endsWith("\n")) {
        jsonl.append("\n");
      }
    }
    String result = jsonl.toString().stripTrailing();
    return result.getBytes(StandardCharsets.UTF_8);
  }

  private byte[] decompressZstd(byte[] compressed) {
    @SuppressWarnings("deprecation")
    long decompressedSize = Zstd.decompressedSize(compressed);
    if (decompressedSize <= 0 || decompressedSize > MAX_DECOMPRESSED_SIZE) {
      decompressedSize = Math.min(compressed.length * 10L, MAX_DECOMPRESSED_SIZE);
    }
    return Zstd.decompress(compressed, (int) decompressedSize);
  }

  static BlockCoordinates parseBlockUrl(String blockUrl) {
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

    return new BlockCoordinates(bucket, key, startByte, endByte);
  }

  record BlockCoordinates(String bucket, String key, long startByte, long endByte) {}
}
