package org.dreamhorizon.pulsealertscron.rest.io;

import io.reactivex.rxjava3.core.SingleConverter;
import java.util.concurrent.CompletionStage;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulsealertscron.util.CompletableFutureUtils;

@Value
@Slf4j
public class RestResponse {
  public static <T> SingleConverter<T, CompletionStage<Response<T>>> jaxrsRestHandler() {
    return single -> single
        .map(Response::successfulResponse)
        .to(CompletableFutureUtils::fromSingle);
  }

  public static SingleConverter<jakarta.ws.rs.core.Response, CompletionStage<jakarta.ws.rs.core.Response>> toCompletion() {
    return single -> single.to(CompletableFutureUtils::fromSingle);
  }
}

