package org.dreamhorizon.pulseserver.client.athena.models;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.athena.model.ResultSet;

public class ResultSetWithTokenTest {

  @Test
  void shouldCreateWithNoArgsConstructor() {
    ResultSetWithToken token = new ResultSetWithToken();
    
    assertThat(token).isNotNull();
    assertThat(token.getResultSet()).isNull();
    assertThat(token.getNextToken()).isNull();
  }

  @Test
  void shouldCreateWithAllArgsConstructor() {
    ResultSet resultSet = ResultSet.builder().build();
    String nextToken = "token-123";
    
    ResultSetWithToken token = new ResultSetWithToken(resultSet, nextToken);
    
    assertThat(token).isNotNull();
    assertThat(token.getResultSet()).isEqualTo(resultSet);
    assertThat(token.getNextToken()).isEqualTo(nextToken);
  }

  @Test
  void shouldCreateWithBuilder() {
    ResultSet resultSet = ResultSet.builder().build();
    String nextToken = "token-456";
    
    ResultSetWithToken token = ResultSetWithToken.builder()
        .resultSet(resultSet)
        .nextToken(nextToken)
        .build();
    
    assertThat(token).isNotNull();
    assertThat(token.getResultSet()).isEqualTo(resultSet);
    assertThat(token.getNextToken()).isEqualTo(nextToken);
  }

  @Test
  void shouldSetAndGetResultSet() {
    ResultSetWithToken token = new ResultSetWithToken();
    ResultSet resultSet = ResultSet.builder().build();
    
    token.setResultSet(resultSet);
    
    assertThat(token.getResultSet()).isEqualTo(resultSet);
  }

  @Test
  void shouldSetAndGetNextToken() {
    ResultSetWithToken token = new ResultSetWithToken();
    String nextToken = "token-789";
    
    token.setNextToken(nextToken);
    
    assertThat(token.getNextToken()).isEqualTo(nextToken);
  }
}
