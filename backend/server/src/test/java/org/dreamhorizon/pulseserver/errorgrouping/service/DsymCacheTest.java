package org.dreamhorizon.pulseserver.errorgrouping.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Vertx;
import java.util.Optional;
import org.dreamhorizon.pulseserver.errorgrouping.model.UploadMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DsymCacheTest {

  @Mock
  private SymbolFileService symbolFileService;

  private Vertx vertx;
  private DsymCache cache;

  @BeforeEach
  void setUp() {
    vertx = Vertx.vertx();
    cache = new DsymCache(vertx, symbolFileService);
  }

  @AfterEach
  void tearDown() {
    if (vertx != null) {
      vertx.close();
    }
  }

  private static UploadMetadata key() {
    return UploadMetadata.builder()
        .projectId("p1")
        .platform("iOS")
        .appVersion("1.0")
        .versionCode("1")
        .bundleId("com.example.app")
        .type("DSYM")
        .build();
  }

  @Test
  void getDsym_returnsBytesWhenPresent() {
    byte[] payload = new byte[] {0x50, 0x4b, 0x03, 0x04};
    when(symbolFileService.readFileAsBytes(any(UploadMetadata.class))).thenReturn(Single.just(payload));

    Optional<byte[]> got = cache.getDsym(key()).blockingGet();
    assertTrue(got.isPresent());
    assertEquals(payload, got.get());
  }

  @Test
  void getDsym_emptyBytes_triggersNegativeCacheAndReturnsEmpty() {
    when(symbolFileService.readFileAsBytes(any(UploadMetadata.class))).thenReturn(Single.just(new byte[0]));

    Optional<byte[]> first = cache.getDsym(key()).blockingGet();
    assertFalse(first.isPresent());

    Optional<byte[]> second = cache.getDsym(key()).blockingGet();
    assertFalse(second.isPresent());
  }

  @Test
  void getDsym_error_triggersNegativeCacheAndReturnsEmpty() {
    when(symbolFileService.readFileAsBytes(any(UploadMetadata.class)))
        .thenReturn(Single.error(new RuntimeException("s3 down")));

    Optional<byte[]> got = cache.getDsym(key()).blockingGet();
    assertFalse(got.isPresent());
  }
}
