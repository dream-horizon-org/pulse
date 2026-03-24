package org.dreamhorizon.pulseserver.service.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.dreamhorizon.pulseserver.service.configs.IS3BucketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xerial.snappy.Snappy;

@ExtendWith(MockitoExtension.class)
class SessionBlockFetcherTest {

  @Mock
  IS3BucketClient s3BucketClient;

  SessionBlockFetcher sessionBlockFetcher;

  @BeforeEach
  void setUp() {
    sessionBlockFetcher = new SessionBlockFetcher(s3BucketClient);
  }

  @Nested
  class FetchBlocks {

    @Test
    void shouldDecompressAndConcatenateMultipleBlocks() throws Exception {
      String jsonl1 = "{\"event\":1}\n";
      String jsonl2 = "{\"event\":2}\n";
      byte[] compressed1 = Snappy.compress(jsonl1.getBytes(StandardCharsets.UTF_8));
      byte[] compressed2 = Snappy.compress(jsonl2.getBytes(StandardCharsets.UTF_8));

      when(s3BucketClient.getObjectRange(eq("bucket"), eq("key1"), eq(0L), eq(100L)))
          .thenReturn(Single.just(compressed1));
      when(s3BucketClient.getObjectRange(eq("bucket"), eq("key2"), eq(101L), eq(200L)))
          .thenReturn(Single.just(compressed2));

      List<String> urls = List.of(
          "s3://bucket/key1?range=bytes=0-100",
          "s3://bucket/key2?range=bytes=101-200");

      byte[] result = sessionBlockFetcher.fetchBlocks(urls).blockingGet();

      String expected = "{\"event\":1}\n{\"event\":2}\n";
      assertThat(new String(result, StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    @Test
    void shouldAppendNewlineWhenBlockDoesNotEndWithNewline() throws Exception {
      String jsonl = "{\"event\":1}"; // no trailing newline
      byte[] compressed = Snappy.compress(jsonl.getBytes(StandardCharsets.UTF_8));

      when(s3BucketClient.getObjectRange(eq("bucket"), eq("key"), eq(0L), eq(50L)))
          .thenReturn(Single.just(compressed));

      byte[] result = sessionBlockFetcher.fetchBlocks(
          List.of("s3://bucket/key?range=bytes=0-50")).blockingGet();

      assertThat(new String(result, StandardCharsets.UTF_8)).isEqualTo("{\"event\":1}\n");
    }

    @Test
    void shouldNotAddDuplicateNewlineWhenBlockEndsWithNewline() throws Exception {
      String jsonl = "{\"event\":1}\n";
      byte[] compressed = Snappy.compress(jsonl.getBytes(StandardCharsets.UTF_8));

      when(s3BucketClient.getObjectRange(eq("bucket"), eq("key"), eq(0L), eq(50L)))
          .thenReturn(Single.just(compressed));

      byte[] result = sessionBlockFetcher.fetchBlocks(
          List.of("s3://bucket/key?range=bytes=0-50")).blockingGet();

      assertThat(new String(result, StandardCharsets.UTF_8)).isEqualTo("{\"event\":1}\n");
    }

    @Test
    void shouldThrowWhenBlockUrlHasInvalidRangeFormat() {
      List<String> urls = List.of("s3://bucket/key?range=invalid");

      assertThatThrownBy(() -> sessionBlockFetcher.fetchBlocks(urls).blockingGet())
          .hasCauseInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid block URL range format");
    }

    @Test
    void shouldThrowWhenBlockUrlMissingRangeQuery() {
      List<String> urls = List.of("s3://bucket/key");

      assertThatThrownBy(() -> sessionBlockFetcher.fetchBlocks(urls).blockingGet())
          .isInstanceOf(Exception.class);
    }

    @Test
    void shouldThrowServiceErrorWhenSnappyBlockSizeIsZero() throws Exception {
      byte[] emptyCompressed = Snappy.compress(new byte[0]);

      when(s3BucketClient.getObjectRange(eq("bucket"), eq("key"), eq(0L), eq(100L)))
          .thenReturn(Single.just(emptyCompressed));

      assertThatThrownBy(() -> sessionBlockFetcher.fetchBlocks(
              List.of("s3://bucket/key?range=bytes=0-100")).blockingGet())
          .hasCauseInstanceOf(jakarta.ws.rs.WebApplicationException.class)
          .hasMessageContaining("Snappy block size out of range");
    }

    @Test
    void shouldThrowServiceErrorWhenSnappyDecompressionFails() {
      byte[] invalidSnappy = new byte[]{1, 2, 3}; // corrupt data

      when(s3BucketClient.getObjectRange(eq("bucket"), eq("key"), eq(0L), eq(100L)))
          .thenReturn(Single.just(invalidSnappy));

      assertThatThrownBy(() -> sessionBlockFetcher.fetchBlocks(
              List.of("s3://bucket/key?range=bytes=0-100")).blockingGet())
          .hasCauseInstanceOf(jakarta.ws.rs.WebApplicationException.class)
          .hasMessageContaining("Failed to decompress Snappy block");
    }

    @Test
    void shouldPropagateErrorWhenS3FetchFails() {
      when(s3BucketClient.getObjectRange(eq("bucket"), eq("key"), eq(0L), eq(100L)))
          .thenReturn(Single.error(new RuntimeException("S3 connection failed")));

      sessionBlockFetcher.fetchBlocks(List.of("s3://bucket/key?range=bytes=0-100"))
          .test()
          .assertError(RuntimeException.class)
          .assertError(throwable -> throwable.getMessage().contains("S3 connection"));
    }

    @Test
    void shouldCallS3WithCorrectBucketKeyAndRange() throws Exception {
      String jsonl = "{\"x\":1}\n";
      byte[] compressed = Snappy.compress(jsonl.getBytes(StandardCharsets.UTF_8));

      when(s3BucketClient.getObjectRange(eq("my-bucket"), eq("path/to/block.jsonl"), eq(0L), eq(999L)))
          .thenReturn(Single.just(compressed));

      sessionBlockFetcher.fetchBlocks(
          List.of("s3://my-bucket/path/to/block.jsonl?range=bytes=0-999")).blockingGet();

      verify(s3BucketClient).getObjectRange("my-bucket", "path/to/block.jsonl", 0L, 999L);
    }
  }
}
