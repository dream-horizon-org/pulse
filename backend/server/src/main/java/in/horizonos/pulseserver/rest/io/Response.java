package in.horizonos.pulseserver.rest.io;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.horizonos.pulseserver.rest.Error;

public class Response<T> {

  private T data;
  private Error error;

  @JsonIgnore
  private int httpStatusCode = 200;

  @JsonCreator()
  Response(
      @JsonProperty("data")
      T data,
      @JsonProperty("error")
      Error error) {
    this.data = data;
    this.error = error;
  }

  Response(T data) {
    this.data = data;
  }

  Response(Error error, int httpStatusCode) {
    this.error = error;
    this.httpStatusCode = httpStatusCode;
  }

  Response(Error error) {
    this.error = error;
  }

  public static <T> Response<T> successfulResponse(T data) {
    return new Response<T>(data);
  }

  public static Response errorResponse(Error error, int httpStatusCode) {
    return new Response(error, httpStatusCode);
  }

  public T getData() {
    return data;
  }

  public Error getError() {
    return error;
  }

}
