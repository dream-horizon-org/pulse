package org.dreamhorizon.pulseserver.client.athena.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.athena.model.ResultSet;

class ResultSetWithTokenTest {

  @Nested
  class TestResultSetWithToken {

    @Test
    void shouldCreateWithNoArgs() {
      ResultSetWithToken resultSetWithToken = new ResultSetWithToken();
      assertNotNull(resultSetWithToken);
    }

    @Test
    void shouldCreateWithAllArgs() {
      ResultSet resultSet = ResultSet.builder().build();
      String nextToken = "token-123";

      ResultSetWithToken resultSetWithToken = new ResultSetWithToken(resultSet, nextToken);

      assertEquals(resultSet, resultSetWithToken.getResultSet());
      assertEquals(nextToken, resultSetWithToken.getNextToken());
    }

    @Test
    void shouldCreateWithBuilder() {
      ResultSet resultSet = ResultSet.builder().build();
      String nextToken = "token-456";

      ResultSetWithToken resultSetWithToken = ResultSetWithToken.builder()
          .resultSet(resultSet)
          .nextToken(nextToken)
          .build();

      assertEquals(resultSet, resultSetWithToken.getResultSet());
      assertEquals(nextToken, resultSetWithToken.getNextToken());
    }

    @Test
    void shouldSetAndGetFields() {
      ResultSetWithToken resultSetWithToken = new ResultSetWithToken();
      ResultSet resultSet = ResultSet.builder().build();
      String nextToken = "token-789";

      resultSetWithToken.setResultSet(resultSet);
      resultSetWithToken.setNextToken(nextToken);

      assertEquals(resultSet, resultSetWithToken.getResultSet());
      assertEquals(nextToken, resultSetWithToken.getNextToken());
    }

    @Test
    void shouldHandleNullValues() {
      ResultSetWithToken resultSetWithToken = ResultSetWithToken.builder().build();

      assertNull(resultSetWithToken.getResultSet());
      assertNull(resultSetWithToken.getNextToken());
    }

    @Test
    void shouldHandleNullNextToken() {
      ResultSet resultSet = ResultSet.builder().build();

      ResultSetWithToken resultSetWithToken = ResultSetWithToken.builder()
          .resultSet(resultSet)
          .nextToken(null)
          .build();

      assertEquals(resultSet, resultSetWithToken.getResultSet());
      assertNull(resultSetWithToken.getNextToken());
    }

    @Test
    void shouldCreateMultipleInstances() {
      ResultSet resultSet = ResultSet.builder().build();
      String originalToken = "original-token";

      ResultSetWithToken original = ResultSetWithToken.builder()
          .resultSet(resultSet)
          .nextToken(originalToken)
          .build();

      String newToken = "new-token";
      ResultSetWithToken modified = ResultSetWithToken.builder()
          .resultSet(resultSet)
          .nextToken(newToken)
          .build();

      assertEquals(resultSet, modified.getResultSet());
      assertEquals(newToken, modified.getNextToken());
      assertEquals(originalToken, original.getNextToken());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      ResultSet resultSet = ResultSet.builder().build();
      String nextToken = "token-123";

      ResultSetWithToken r1 = ResultSetWithToken.builder()
          .resultSet(resultSet)
          .nextToken(nextToken)
          .build();

      ResultSetWithToken r2 = ResultSetWithToken.builder()
          .resultSet(resultSet)
          .nextToken(nextToken)
          .build();

      assertEquals(r1, r2);
      assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      ResultSetWithToken resultSetWithToken = ResultSetWithToken.builder()
          .nextToken("token-123")
          .build();

      assertNotNull(resultSetWithToken.toString());
      assertNotNull(resultSetWithToken.toString().contains("token-123"));
    }
  }
}

