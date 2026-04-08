//package org.dreamhorizon.pulseserver.resources.sessionreplay;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertNotNull;
//import static org.junit.jupiter.api.Assertions.assertNull;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//
//import io.reactivex.rxjava3.core.Single;
//import io.vertx.core.Vertx;
//import io.vertx.junit5.VertxExtension;
//import io.vertx.junit5.VertxTestContext;
//import java.util.List;
//import java.util.concurrent.CompletionStage;
//import org.dreamhorizon.pulseserver.resources.session.models.SnapshotSourcesResponse;
//import org.dreamhorizon.pulseserver.rest.io.Response;
//import org.dreamhorizon.pulseserver.service.session.SessionReplayService;
//import org.dreamhorizon.pulseserver.tenant.TenantContext;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Nested;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//@ExtendWith({MockitoExtension.class, VertxExtension.class})
//class SessionReplayTest {
//
//  private static final String SESSION_ID = "ses-mobile-abc123";
//  private static final String TENANT_ID = "proj-ecom-42";
//
//  @Mock
//  private SessionReplayService sessionReplayService;
//
//  private SessionReplay resource;
//
//  @BeforeEach
//  void setUp() {
//    resource = new SessionReplay(sessionReplayService);
//    TenantContext.setTenantId(TENANT_ID);
//  }
//
//  @AfterEach
//  void tearDown() {
//    TenantContext.clear();
//  }
//
//  @Nested
//  class GetBlockSources {
//
//    @Test
//    void returnsSourcesWhenNoSourceParam(Vertx vertx, VertxTestContext testContext) {
//      vertx.runOnContext(v -> {
//        SnapshotSourcesResponse mockResponse = SnapshotSourcesResponse.builder()
//            .sessionId(SESSION_ID)
//            .snapshotSource("mobile")
//            .sources(List.of(
//                SnapshotSourcesResponse.BlockSource.builder()
//                    .source("blob")
//                    .blobKey("0")
//                    .startTimestamp("2026-03-11T10:00:00.000Z")
//                    .endTimestamp("2026-03-11T10:02:15.000Z")
//                    .build()))
//            .build();
//
//        when(sessionReplayService.getBlockSources(SESSION_ID)).thenReturn(Single.just(mockResponse));
//
//        CompletionStage<?> result = resource.getSnapshots(SESSION_ID, null, null, null, null);
//
//        result.whenComplete((resp, err) -> {
//          testContext.verify(() -> {
//            assertNull(err);
//            assertNotNull(resp);
//            @SuppressWarnings("unchecked")
//            Response<SnapshotSourcesResponse> typed = (Response<SnapshotSourcesResponse>) resp;
//            assertNotNull(typed.getData());
//            assertEquals(SESSION_ID, typed.getData().getSessionId());
//            assertEquals("mobile", typed.getData().getSnapshotSource());
//            assertEquals(1, typed.getData().getSources().size());
//            assertEquals("0", typed.getData().getSources().get(0).getBlobKey());
//            verify(sessionReplayService).getBlockSources(SESSION_ID);
//          });
//          testContext.completeNow();
//        });
//      });
//    }
//
//    @Test
//    void propagatesErrorWhenServiceFails(Vertx vertx, VertxTestContext testContext) {
//      vertx.runOnContext(v -> {
//        when(sessionReplayService.getBlockSources(SESSION_ID))
//            .thenReturn(Single.error(new IllegalArgumentException("Session not found")));
//
//        CompletionStage<?> result = resource.getSnapshots(SESSION_ID, null, null, null, null);
//
//        result.whenComplete((resp, err) -> {
//          testContext.verify(() -> {
//            assertNotNull(err);
//            assertEquals(IllegalArgumentException.class, err.getCause().getClass());
//            verify(sessionReplayService).getBlockSources(SESSION_ID);
//          });
//          testContext.completeNow();
//        });
//      });
//    }
//  }
//
//  @Nested
//  class GetBlockData {
//
//    @Test
//    void returnsJsonlWhenSourceBlobAndDecompressTrue(Vertx vertx, VertxTestContext testContext) {
//      vertx.runOnContext(v -> {
//        byte[] mockData = "{\"timestamp\":1708067520000,\"type\":4}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
//        when(sessionReplayService.fetchBlockData(SESSION_ID, 0, 0, true))
//            .thenReturn(Single.just(mockData));
//
//        CompletionStage<?> result = resource.getSnapshots(SESSION_ID, "blob", 0, 0, true);
//
//        result.whenComplete((resp, err) -> {
//          testContext.verify(() -> {
//            assertNull(err);
//            assertNotNull(resp);
//            jakarta.ws.rs.core.Response jaxRs = (jakarta.ws.rs.core.Response) resp;
//            assertEquals(200, jaxRs.getStatus());
//            assertEquals("application/jsonl", jaxRs.getMediaType().toString());
//            assertEquals("max-age=3600", jaxRs.getHeaderString("Cache-Control"));
//            verify(sessionReplayService).fetchBlockData(SESSION_ID, 0, 0, true);
//          });
//          testContext.completeNow();
//        });
//      });
//    }
//
//    @Test
//    void returnsOctetStreamWhenDecompressFalse(Vertx vertx, VertxTestContext testContext) {
//      vertx.runOnContext(v -> {
//        byte[] mockData = new byte[]{0, 0, 0, 10, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
//        when(sessionReplayService.fetchBlockData(SESSION_ID, 0, 1, false))
//            .thenReturn(Single.just(mockData));
//
//        CompletionStage<?> result = resource.getSnapshots(SESSION_ID, "blob", 0, 1, false);
//
//        result.whenComplete((resp, err) -> {
//          testContext.verify(() -> {
//            assertNull(err);
//            jakarta.ws.rs.core.Response jaxRs = (jakarta.ws.rs.core.Response) resp;
//            assertEquals("application/octet-stream", jaxRs.getMediaType().toString());
//            verify(sessionReplayService).fetchBlockData(SESSION_ID, 0, 1, false);
//          });
//          testContext.completeNow();
//        });
//      });
//    }
//
//    @Test
//    void failsWhenSourceBlobButMissingBlobKeys(Vertx vertx, VertxTestContext testContext) {
//      vertx.runOnContext(v -> {
//        CompletionStage<?> result = resource.getSnapshots(SESSION_ID, "blob", null, null, true);
//
//        result.whenComplete((resp, err) -> {
//          testContext.verify(() -> {
//            assertNotNull(err);
//            verify(sessionReplayService, org.mockito.Mockito.never()).fetchBlockData(
//                org.mockito.ArgumentMatchers.anyString(),
//                org.mockito.ArgumentMatchers.anyInt(),
//                org.mockito.ArgumentMatchers.anyInt(),
//                org.mockito.ArgumentMatchers.anyBoolean());
//          });
//          testContext.completeNow();
//        });
//      });
//    }
//
//    @Test
//    void failsWhenInvalidSource(Vertx vertx, VertxTestContext testContext) {
//      vertx.runOnContext(v -> {
//        CompletionStage<?> result = resource.getSnapshots(SESSION_ID, "invalid", 0, 0, true);
//
//        result.whenComplete((resp, err) -> {
//          testContext.verify(() -> {
//            assertNotNull(err);
//            verify(sessionReplayService, org.mockito.Mockito.never()).fetchBlockData(
//                org.mockito.ArgumentMatchers.anyString(),
//                org.mockito.ArgumentMatchers.anyInt(),
//                org.mockito.ArgumentMatchers.anyInt(),
//                org.mockito.ArgumentMatchers.anyBoolean());
//          });
//          testContext.completeNow();
//        });
//      });
//    }
//  }
//}
