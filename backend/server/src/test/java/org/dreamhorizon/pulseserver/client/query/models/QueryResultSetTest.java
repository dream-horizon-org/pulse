package org.dreamhorizon.pulseserver.client.query.models;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.json.JsonArray;
import org.junit.jupiter.api.Test;

public class QueryResultSetTest {

  @Test
  void shouldCreateWithNoArgsConstructor() {
    QueryResultSet resultSet = new QueryResultSet();
    
    assertThat(resultSet).isNotNull();
    assertThat(resultSet.getResultData()).isNull();
    assertThat(resultSet.getNextToken()).isNull();
  }

  @Test
  void shouldCreateWithAllArgsConstructor() {
    JsonArray data = new JsonArray();
    String nextToken = "token-123";
    
    QueryResultSet resultSet = new QueryResultSet(data, nextToken);
    
    assertThat(resultSet.getResultData()).isEqualTo(data);
    assertThat(resultSet.getNextToken()).isEqualTo(nextToken);
  }

  @Test
  void shouldCreateWithBuilder() {
    JsonArray data = new JsonArray();
    String nextToken = "token-456";
    
    QueryResultSet resultSet = QueryResultSet.builder()
        .resultData(data)
        .nextToken(nextToken)
        .build();
    
    assertThat(resultSet.getResultData()).isEqualTo(data);
    assertThat(resultSet.getNextToken()).isEqualTo(nextToken);
  }

  @Test
  void shouldSetAndGetValues() {
    QueryResultSet resultSet = new QueryResultSet();
    JsonArray data = new JsonArray();
    String nextToken = "token-789";
    
    resultSet.setResultData(data);
    resultSet.setNextToken(nextToken);
    
    assertThat(resultSet.getResultData()).isEqualTo(data);
    assertThat(resultSet.getNextToken()).isEqualTo(nextToken);
  }
}

