package org.dreamhorizon.pulseserver.resources.performance.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class QueryRequestTest {

  @Nested
  class TestQueryRequest {

    @Test
    void shouldCreateQueryRequest() {
      QueryRequest request = new QueryRequest();
      assertNotNull(request);
    }

    @Test
    void shouldSetAndGetDataType() {
      QueryRequest request = new QueryRequest();
      request.setDataType(QueryRequest.DataType.TRACES);
      assertEquals(QueryRequest.DataType.TRACES, request.getDataType());
    }

    @Test
    void shouldSetAndGetTimeRange() {
      QueryRequest request = new QueryRequest();
      QueryRequest.TimeRange timeRange = new QueryRequest.TimeRange();
      timeRange.setStart("2023-01-01T00:00:00Z");
      timeRange.setEnd("2023-01-02T00:00:00Z");
      request.setTimeRange(timeRange);
      
      assertNotNull(request.getTimeRange());
      assertEquals("2023-01-01T00:00:00Z", request.getTimeRange().getStart());
      assertEquals("2023-01-02T00:00:00Z", request.getTimeRange().getEnd());
    }

    @Test
    void shouldSetAndGetSelect() {
      QueryRequest request = new QueryRequest();
      List<QueryRequest.SelectItem> selectItems = new ArrayList<>();
      QueryRequest.SelectItem item = new QueryRequest.SelectItem();
      item.setFunction(Functions.APDEX);
      item.setAlias("apdex_alias");
      selectItems.add(item);
      request.setSelect(selectItems);
      
      assertEquals(1, request.getSelect().size());
      assertEquals(Functions.APDEX, request.getSelect().get(0).getFunction());
    }

    @Test
    void shouldSetAndGetFilters() {
      QueryRequest request = new QueryRequest();
      List<QueryRequest.Filter> filters = new ArrayList<>();
      QueryRequest.Filter filter = new QueryRequest.Filter();
      filter.setField("span.name");
      filter.setOperator(QueryRequest.Operator.EQ);
      filter.setValue(List.of("test"));
      filters.add(filter);
      request.setFilters(filters);
      
      assertEquals(1, request.getFilters().size());
      assertEquals("span.name", request.getFilters().get(0).getField());
    }

    @Test
    void shouldSetAndGetGroupBy() {
      QueryRequest request = new QueryRequest();
      List<String> groupBy = List.of("field1", "field2");
      request.setGroupBy(groupBy);
      
      assertEquals(2, request.getGroupBy().size());
      assertTrue(request.getGroupBy().contains("field1"));
    }

    @Test
    void shouldSetAndGetOrderBy() {
      QueryRequest request = new QueryRequest();
      List<QueryRequest.OrderBy> orderBy = new ArrayList<>();
      QueryRequest.OrderBy order = new QueryRequest.OrderBy();
      order.setField("apdex");
      order.setDirection(QueryRequest.Direction.DESC);
      orderBy.add(order);
      request.setOrderBy(orderBy);
      
      assertEquals(1, request.getOrderBy().size());
      assertEquals("apdex", request.getOrderBy().get(0).getField());
    }

    @Test
    void shouldSetAndGetLimit() {
      QueryRequest request = new QueryRequest();
      request.setLimit(100);
      assertEquals(100, request.getLimit());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      QueryRequest r1 = new QueryRequest();
      r1.setLimit(100);
      r1.setDataType(QueryRequest.DataType.TRACES);
      
      QueryRequest r2 = new QueryRequest();
      r2.setLimit(100);
      r2.setDataType(QueryRequest.DataType.TRACES);
      
      assertEquals(r1, r2);
      assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      QueryRequest request = new QueryRequest();
      request.setLimit(50);
      assertNotNull(request.toString());
      assertTrue(request.toString().contains("50"));
    }
  }

  @Nested
  class TestTimeRange {

    @Test
    void shouldCreateTimeRange() {
      QueryRequest.TimeRange timeRange = new QueryRequest.TimeRange();
      assertNotNull(timeRange);
    }

    @Test
    void shouldSetAndGetStart() {
      QueryRequest.TimeRange timeRange = new QueryRequest.TimeRange();
      timeRange.setStart("2023-01-01T00:00:00Z");
      assertEquals("2023-01-01T00:00:00Z", timeRange.getStart());
    }

    @Test
    void shouldSetAndGetEnd() {
      QueryRequest.TimeRange timeRange = new QueryRequest.TimeRange();
      timeRange.setEnd("2023-12-31T23:59:59Z");
      assertEquals("2023-12-31T23:59:59Z", timeRange.getEnd());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      QueryRequest.TimeRange t1 = new QueryRequest.TimeRange();
      t1.setStart("2023-01-01T00:00:00Z");
      t1.setEnd("2023-01-02T00:00:00Z");
      
      QueryRequest.TimeRange t2 = new QueryRequest.TimeRange();
      t2.setStart("2023-01-01T00:00:00Z");
      t2.setEnd("2023-01-02T00:00:00Z");
      
      assertEquals(t1, t2);
      assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      QueryRequest.TimeRange timeRange = new QueryRequest.TimeRange();
      timeRange.setStart("2023-01-01T00:00:00Z");
      assertNotNull(timeRange.toString());
      assertTrue(timeRange.toString().contains("2023-01-01"));
    }
  }

  @Nested
  class TestSelectItem {

    @Test
    void shouldCreateSelectItem() {
      QueryRequest.SelectItem item = new QueryRequest.SelectItem();
      assertNotNull(item);
    }

    @Test
    void shouldSetAndGetFunction() {
      QueryRequest.SelectItem item = new QueryRequest.SelectItem();
      item.setFunction(Functions.CRASH);
      assertEquals(Functions.CRASH, item.getFunction());
    }

    @Test
    void shouldSetAndGetParam() {
      QueryRequest.SelectItem item = new QueryRequest.SelectItem();
      Map<String, String> params = new HashMap<>();
      params.put("field", "Timestamp");
      params.put("bucket", "60m");
      item.setParam(params);
      
      assertEquals(2, item.getParam().size());
      assertEquals("Timestamp", item.getParam().get("field"));
    }

    @Test
    void shouldSetAndGetAlias() {
      QueryRequest.SelectItem item = new QueryRequest.SelectItem();
      item.setAlias("my_alias");
      assertEquals("my_alias", item.getAlias());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      QueryRequest.SelectItem s1 = new QueryRequest.SelectItem();
      s1.setFunction(Functions.ANR);
      s1.setAlias("anr_alias");
      
      QueryRequest.SelectItem s2 = new QueryRequest.SelectItem();
      s2.setFunction(Functions.ANR);
      s2.setAlias("anr_alias");
      
      assertEquals(s1, s2);
      assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      QueryRequest.SelectItem item = new QueryRequest.SelectItem();
      item.setAlias("test_alias");
      assertNotNull(item.toString());
      assertTrue(item.toString().contains("test_alias"));
    }
  }

  @Nested
  class TestFilter {

    @Test
    void shouldCreateFilter() {
      QueryRequest.Filter filter = new QueryRequest.Filter();
      assertNotNull(filter);
    }

    @Test
    void shouldSetAndGetField() {
      QueryRequest.Filter filter = new QueryRequest.Filter();
      filter.setField("PulseType");
      assertEquals("PulseType", filter.getField());
    }

    @Test
    void shouldSetAndGetOperator() {
      QueryRequest.Filter filter = new QueryRequest.Filter();
      filter.setOperator(QueryRequest.Operator.IN);
      assertEquals(QueryRequest.Operator.IN, filter.getOperator());
    }

    @Test
    void shouldSetAndGetValue() {
      QueryRequest.Filter filter = new QueryRequest.Filter();
      filter.setValue(List.of("value1", "value2"));
      assertEquals(2, filter.getValue().size());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      QueryRequest.Filter f1 = new QueryRequest.Filter();
      f1.setField("field1");
      f1.setOperator(QueryRequest.Operator.EQ);
      
      QueryRequest.Filter f2 = new QueryRequest.Filter();
      f2.setField("field1");
      f2.setOperator(QueryRequest.Operator.EQ);
      
      assertEquals(f1, f2);
      assertEquals(f1.hashCode(), f2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      QueryRequest.Filter filter = new QueryRequest.Filter();
      filter.setField("test_field");
      assertNotNull(filter.toString());
      assertTrue(filter.toString().contains("test_field"));
    }
  }

  @Nested
  class TestOrderBy {

    @Test
    void shouldCreateOrderBy() {
      QueryRequest.OrderBy orderBy = new QueryRequest.OrderBy();
      assertNotNull(orderBy);
    }

    @Test
    void shouldSetAndGetField() {
      QueryRequest.OrderBy orderBy = new QueryRequest.OrderBy();
      orderBy.setField("apdex");
      assertEquals("apdex", orderBy.getField());
    }

    @Test
    void shouldSetAndGetDirection() {
      QueryRequest.OrderBy orderBy = new QueryRequest.OrderBy();
      orderBy.setDirection(QueryRequest.Direction.ASC);
      assertEquals(QueryRequest.Direction.ASC, orderBy.getDirection());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      QueryRequest.OrderBy o1 = new QueryRequest.OrderBy();
      o1.setField("field1");
      o1.setDirection(QueryRequest.Direction.DESC);
      
      QueryRequest.OrderBy o2 = new QueryRequest.OrderBy();
      o2.setField("field1");
      o2.setDirection(QueryRequest.Direction.DESC);
      
      assertEquals(o1, o2);
      assertEquals(o1.hashCode(), o2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      QueryRequest.OrderBy orderBy = new QueryRequest.OrderBy();
      orderBy.setField("sort_field");
      assertNotNull(orderBy.toString());
      assertTrue(orderBy.toString().contains("sort_field"));
    }
  }

  @Nested
  class TestDataTypeEnum {

    @Test
    void shouldHaveAllExpectedValues() {
      QueryRequest.DataType[] dataTypes = QueryRequest.DataType.values();
      assertEquals(4, dataTypes.length);
    }

    @Test
    void shouldHaveTracesType() {
      assertEquals("TRACES", QueryRequest.DataType.TRACES.name());
    }

    @Test
    void shouldHaveLogsType() {
      assertEquals("LOGS", QueryRequest.DataType.LOGS.name());
    }

    @Test
    void shouldHaveMetricsType() {
      assertEquals("METRICS", QueryRequest.DataType.METRICS.name());
    }

    @Test
    void shouldHaveExceptionsType() {
      assertEquals("EXCEPTIONS", QueryRequest.DataType.EXCEPTIONS.name());
    }

    @Test
    void shouldGetValueByName() {
      assertEquals(QueryRequest.DataType.TRACES, QueryRequest.DataType.valueOf("TRACES"));
      assertEquals(QueryRequest.DataType.LOGS, QueryRequest.DataType.valueOf("LOGS"));
    }
  }

  @Nested
  class TestDirectionEnum {

    @Test
    void shouldHaveAllExpectedValues() {
      QueryRequest.Direction[] directions = QueryRequest.Direction.values();
      assertEquals(2, directions.length);
    }

    @Test
    void shouldHaveAscDirection() {
      assertEquals("ASC", QueryRequest.Direction.ASC.name());
    }

    @Test
    void shouldHaveDescDirection() {
      assertEquals("DESC", QueryRequest.Direction.DESC.name());
    }

    @Test
    void shouldGetValueByName() {
      assertEquals(QueryRequest.Direction.ASC, QueryRequest.Direction.valueOf("ASC"));
      assertEquals(QueryRequest.Direction.DESC, QueryRequest.Direction.valueOf("DESC"));
    }
  }

  @Nested
  class TestOperatorEnum {

    @Test
    void shouldHaveAllExpectedValues() {
      QueryRequest.Operator[] operators = QueryRequest.Operator.values();
      assertEquals(4, operators.length);
    }

    @Test
    void shouldHaveLikeOperator() {
      QueryRequest.Operator op = QueryRequest.Operator.LIKE;
      assertEquals("like", op.getDisplayName());
    }

    @Test
    void shouldHaveInOperator() {
      QueryRequest.Operator op = QueryRequest.Operator.IN;
      assertEquals("In", op.getDisplayName());
    }

    @Test
    void shouldHaveEqOperator() {
      QueryRequest.Operator op = QueryRequest.Operator.EQ;
      assertEquals("=", op.getDisplayName());
    }

    @Test
    void shouldHaveAdditionalOperator() {
      QueryRequest.Operator op = QueryRequest.Operator.ADDITIONAL;
      assertEquals("", op.getDisplayName());
    }

    @Test
    void shouldGetValueByName() {
      assertEquals(QueryRequest.Operator.LIKE, QueryRequest.Operator.valueOf("LIKE"));
      assertEquals(QueryRequest.Operator.IN, QueryRequest.Operator.valueOf("IN"));
      assertEquals(QueryRequest.Operator.EQ, QueryRequest.Operator.valueOf("EQ"));
      assertEquals(QueryRequest.Operator.ADDITIONAL, QueryRequest.Operator.valueOf("ADDITIONAL"));
    }
  }
}

