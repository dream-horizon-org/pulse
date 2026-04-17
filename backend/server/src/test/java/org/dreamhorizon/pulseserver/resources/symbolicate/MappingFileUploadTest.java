package org.dreamhorizon.pulseserver.resources.symbolicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.util.serialization.ObjectMapperFactory;
import org.dreamhorizon.pulseserver.errorgrouping.service.SymbolFileService;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith({MockitoExtension.class, VertxExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class MappingFileUploadTest {

  @Mock
  private SymbolFileService symbolFileService;

  @Mock
  private MultipartFormDataInput multipartInput;

  private MappingFileUpload mappingFileUpload;

  private final ObjectMapper objectMapper = ObjectMapperFactory.get();

  @BeforeEach
  void setUp() {
    mappingFileUpload = new MappingFileUpload(objectMapper, symbolFileService);
    ProjectContext.setProjectId("test-project");
  }

  @AfterEach
  void tearDown() {
    ProjectContext.clear();
  }

  private InputPart createInputPart(String fileName, String content) {
    try {
      InputPart part = mock(InputPart.class);
      MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
      headers.add("Content-Disposition", "form-data; name=\"fileContent\"; filename=\"" + fileName + "\"");
      lenient().when(part.getHeaders()).thenReturn(headers);
      lenient().when(part.getBody(InputStream.class, null))
          .thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
      return part;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private InputPart createMetadataPart(String metadataJson) {
    try {
      InputPart part = mock(InputPart.class);
      lenient().when(part.getBody(String.class, null)).thenReturn(metadataJson);
      return part;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Nested
  class SuccessfulUploadTests {

    @Test
    void shouldUploadFileSuccessfully(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        ProjectContext.setProjectId("test-project");
        String metadataJson = "[{\"fileName\":\"mapping.txt\",\"type\":\"mapping\",\"appVersion\":\"1.0.0\",\"versionCode\":\"1\",\"platform\":\"android\"}]";
        InputPart metadataPart = createMetadataPart(metadataJson);
        InputPart filePart = createInputPart("mapping.txt", "source map content");

        Map<String, List<InputPart>> formPartsMap = new HashMap<>();
        formPartsMap.put("metadata", List.of(metadataPart));
        formPartsMap.put("fileContent", List.of(filePart));
        doReturn(formPartsMap).when(multipartInput).getFormDataMap();
        when(symbolFileService.uploadFiles(anyString(), anyList(), anyList())).thenReturn(Single.just(true));

        CompletionStage<Response<Boolean>> result = mappingFileUpload.uploadFile(multipartInput);

        result.whenComplete((response, error) -> {
          if (error != null) {
            testContext.failNow(error);
            return;
          }
          testContext.verify(() -> {
            assertThat(response).isNotNull();
            assertThat(response.getError()).isNull();
            assertThat(response.getData()).isTrue();
            verify(symbolFileService).uploadFiles(anyString(), anyList(), anyList());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldHandleMultipleFiles(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        ProjectContext.setProjectId("test-project");
        String metadataJson = "["
            + "{\"fileName\":\"mapping1.txt\",\"type\":\"mapping\",\"appVersion\":\"1.0.0\",\"versionCode\":\"1\",\"platform\":\"android\"},"
            + "{\"fileName\":\"mapping2.txt\",\"type\":\"mapping\",\"appVersion\":\"1.0.0\",\"versionCode\":\"1\",\"platform\":\"android\"}"
            + "]";
        InputPart metadataPart = createMetadataPart(metadataJson);
        InputPart filePart1 = createInputPart("mapping1.txt", "content1");
        InputPart filePart2 = createInputPart("mapping2.txt", "content2");

        Map<String, List<InputPart>> formPartsMap = new HashMap<>();
        formPartsMap.put("metadata", List.of(metadataPart));
        formPartsMap.put("fileContent", List.of(filePart1, filePart2));
        doReturn(formPartsMap).when(multipartInput).getFormDataMap();
        when(symbolFileService.uploadFiles(anyString(), anyList(), anyList())).thenReturn(Single.just(true));

        CompletionStage<Response<Boolean>> result = mappingFileUpload.uploadFile(multipartInput);

        result.whenComplete((response, error) -> {
          if (error != null) {
            testContext.failNow(error);
            return;
          }
          testContext.verify(() -> {
            assertThat(response).isNotNull();
            assertThat(response.getError()).isNull();
            assertThat(response.getData()).isTrue();
          });
          testContext.completeNow();
        });
      });
    }
  }

  @Nested
  class ErrorHandlingTests {

    @Test
    void shouldReturnFalseWhenMetadataPartMissing(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        Map<String, List<InputPart>> formPartsMap = new HashMap<>();
        formPartsMap.put("fileContent", List.of(mock(InputPart.class)));
        doReturn(formPartsMap).when(multipartInput).getFormDataMap();

        CompletionStage<Response<Boolean>> result = mappingFileUpload.uploadFile(multipartInput);

        result.whenComplete((response, error) -> {
          if (error != null) {
            testContext.failNow(error);
            return;
          }
          testContext.verify(() -> {
            assertThat(response).isNotNull();
            assertThat(response.getError()).isNull();
            assertThat(response.getData()).isFalse();
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldReturnFalseWhenMetadataIsEmpty(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InputPart metadataPart = createMetadataPart("");
        Map<String, List<InputPart>> formPartsMap = new HashMap<>();
        formPartsMap.put("metadata", List.of(metadataPart));
        doReturn(formPartsMap).when(multipartInput).getFormDataMap();

        CompletionStage<Response<Boolean>> result = mappingFileUpload.uploadFile(multipartInput);

        result.whenComplete((response, error) -> {
          if (error != null) {
            testContext.failNow(error);
            return;
          }
          testContext.verify(() -> {
            assertThat(response).isNotNull();
            assertThat(response.getError()).isNull();
            assertThat(response.getData()).isFalse();
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldReturnFalseWhenMetadataIsNull(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        try {
          InputPart metadataPart = mock(InputPart.class);
          lenient().when(metadataPart.getBody(String.class, null)).thenReturn(null);
          Map<String, List<InputPart>> formPartsMap = new HashMap<>();
          formPartsMap.put("metadata", List.of(metadataPart));
          doReturn(formPartsMap).when(multipartInput).getFormDataMap();

          CompletionStage<Response<Boolean>> result = mappingFileUpload.uploadFile(multipartInput);

          result.whenComplete((response, error) -> {
            if (error != null) {
              testContext.failNow(error);
              return;
            }
            testContext.verify(() -> {
              assertThat(response).isNotNull();
              assertThat(response.getError()).isNull();
              assertThat(response.getData()).isFalse();
            });
            testContext.completeNow();
          });
        } catch (Exception e) {
          testContext.failNow(e);
        }
      });
    }

    @Test
    void shouldReturnErrorWhenMetadataJsonIsInvalid(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InputPart metadataPart = createMetadataPart("invalid json");
        Map<String, List<InputPart>> formPartsMap = new HashMap<>();
        formPartsMap.put("metadata", List.of(metadataPart));
        doReturn(formPartsMap).when(multipartInput).getFormDataMap();

        CompletionStage<Response<Boolean>> result = mappingFileUpload.uploadFile(multipartInput);

        result.whenComplete((response, error) -> {
          if (error == null) {
            testContext.failNow(new AssertionError("Expected error but got response: " + response));
            return;
          }
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldReturnFalseWhenMetadataListIsEmpty(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        String metadataJson = "[]";
        InputPart metadataPart = createMetadataPart(metadataJson);
        Map<String, List<InputPart>> formPartsMap = new HashMap<>();
        formPartsMap.put("metadata", List.of(metadataPart));
        doReturn(formPartsMap).when(multipartInput).getFormDataMap();

        CompletionStage<Response<Boolean>> result = mappingFileUpload.uploadFile(multipartInput);

        result.whenComplete((response, error) -> {
          if (error != null) {
            testContext.failNow(error);
            return;
          }
          testContext.verify(() -> {
            assertThat(response).isNotNull();
            assertThat(response.getError()).isNull();
            assertThat(response.getData()).isFalse();
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldReturnErrorWhenProjectIdMissing(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        ProjectContext.clear();
        String metadataJson = "[{\"fileName\":\"mapping.txt\",\"type\":\"mapping\",\"appVersion\":\"1.0.0\",\"versionCode\":\"1\",\"platform\":\"android\"}]";
        InputPart metadataPart = createMetadataPart(metadataJson);
        Map<String, List<InputPart>> formPartsMap = new HashMap<>();
        formPartsMap.put("metadata", List.of(metadataPart));
        doReturn(formPartsMap).when(multipartInput).getFormDataMap();

        CompletionStage<Response<Boolean>> result = mappingFileUpload.uploadFile(multipartInput);

        result.whenComplete((response, error) -> {
          if (error == null) {
            testContext.failNow(new AssertionError("Expected error but got response: " + response));
            return;
          }
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldReturnErrorWhenUploadServiceFails(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        String metadataJson = "[{\"fileName\":\"mapping.txt\",\"type\":\"mapping\",\"appVersion\":\"1.0.0\",\"versionCode\":\"1\",\"platform\":\"android\"}]";
        InputPart metadataPart = createMetadataPart(metadataJson);
        InputPart filePart = createInputPart("mapping.txt", "content");

        Map<String, List<InputPart>> formPartsMap = new HashMap<>();
        formPartsMap.put("metadata", List.of(metadataPart));
        formPartsMap.put("fileContent", List.of(filePart));
        doReturn(formPartsMap).when(multipartInput).getFormDataMap();
        when(symbolFileService.uploadFiles(anyString(), anyList(), anyList()))
            .thenReturn(Single.error(new RuntimeException("Upload failed")));

        CompletionStage<Response<Boolean>> result = mappingFileUpload.uploadFile(multipartInput);

        result.whenComplete((response, error) -> {
          if (error == null) {
            testContext.failNow(new AssertionError("Expected error but got response: " + response));
            return;
          }
          testContext.completeNow();
        });
      });
    }
  }
}
