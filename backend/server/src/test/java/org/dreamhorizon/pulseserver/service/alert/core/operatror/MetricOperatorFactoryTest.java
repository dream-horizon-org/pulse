package org.dreamhorizon.pulseserver.service.alert.core.operatror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.dreamhorizon.pulseserver.service.alert.core.models.MetricOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MetricOperatorFactoryTest {

  private MetricOperatorFactory factory;
  private GreaterThanMetricOperatorProcessor greaterThanProcessor;
  private LessThanMetricOperatorProcessor lessThanProcessor;
  private GreaterThanEqualToMetricOperatorProcessor greaterThanEqualProcessor;
  private LessThanEqualToMetricOperatorProcessor lessThanEqualProcessor;

  @BeforeEach
  void setUp() {
    greaterThanProcessor = mock(GreaterThanMetricOperatorProcessor.class);
    lessThanProcessor = mock(LessThanMetricOperatorProcessor.class);
    greaterThanEqualProcessor = mock(GreaterThanEqualToMetricOperatorProcessor.class);
    lessThanEqualProcessor = mock(LessThanEqualToMetricOperatorProcessor.class);

    factory = new MetricOperatorFactory(
        greaterThanProcessor,
        lessThanProcessor,
        greaterThanEqualProcessor,
        lessThanEqualProcessor
    );
  }

  @Nested
  class TestConstructor {

    @Test
    void shouldCreateFactoryWithAllProcessors() {
      assertNotNull(factory);
      assertNotNull(factory.getMetricOperatorMap());
      assertEquals(4, factory.getMetricOperatorMap().size());
    }
  }

  @Nested
  class TestGetProcessor {

    @Test
    void shouldReturnGreaterThanProcessor() {
      MetricOperatorProcessor processor = factory.getProcessor(MetricOperator.GREATER_THAN);

      assertNotNull(processor);
      assertEquals(greaterThanProcessor, processor);
    }

    @Test
    void shouldReturnLessThanProcessor() {
      MetricOperatorProcessor processor = factory.getProcessor(MetricOperator.LESS_THAN);

      assertNotNull(processor);
      assertEquals(lessThanProcessor, processor);
    }

    @Test
    void shouldReturnGreaterThanEqualProcessor() {
      MetricOperatorProcessor processor = factory.getProcessor(MetricOperator.GREATER_THAN_EQUAL);

      assertNotNull(processor);
      assertEquals(greaterThanEqualProcessor, processor);
    }

    @Test
    void shouldReturnLessThanEqualProcessor() {
      MetricOperatorProcessor processor = factory.getProcessor(MetricOperator.LESS_THAN_EQUAL);

      assertNotNull(processor);
      assertEquals(lessThanEqualProcessor, processor);
    }

    @Test
    void shouldThrowExceptionForNullOperator() {
      // When operator is null, ImmutableCollections map throws NullPointerException
      assertThrows(
          NullPointerException.class,
          () -> factory.getProcessor(null)
      );
    }
  }

  @Nested
  class TestMetricOperatorMap {

    @Test
    void shouldHaveGreaterThanInMap() {
      assertTrue(factory.getMetricOperatorMap().containsKey(MetricOperator.GREATER_THAN));
    }

    @Test
    void shouldHaveLessThanInMap() {
      assertTrue(factory.getMetricOperatorMap().containsKey(MetricOperator.LESS_THAN));
    }

    @Test
    void shouldHaveGreaterThanEqualInMap() {
      assertTrue(factory.getMetricOperatorMap().containsKey(MetricOperator.GREATER_THAN_EQUAL));
    }

    @Test
    void shouldHaveLessThanEqualInMap() {
      assertTrue(factory.getMetricOperatorMap().containsKey(MetricOperator.LESS_THAN_EQUAL));
    }
  }

  @Nested
  class TestSettersAndGetters {

    @Test
    void shouldReturnMetricOperatorMap() {
      assertNotNull(factory.getMetricOperatorMap());
    }
  }

  // Tests for GreaterThanMetricOperatorProcessor
  @Nested
  class TestGreaterThanMetricOperatorProcessor {

    private GreaterThanMetricOperatorProcessor processor;

    @BeforeEach
    void setUp() {
      processor = new GreaterThanMetricOperatorProcessor();
    }

    @Test
    void shouldReturnTrueWhenActualValueIsGreaterThanThreshold() {
      assertTrue(processor.isFiring(0.5f, 0.6f));
    }

    @Test
    void shouldReturnFalseWhenActualValueIsLessThanThreshold() {
      assertFalse(processor.isFiring(0.5f, 0.4f));
    }

    @Test
    void shouldReturnFalseWhenActualValueEqualsThreshold() {
      assertFalse(processor.isFiring(0.5f, 0.5f));
    }

    @Test
    void shouldHandleZeroValues() {
      assertTrue(processor.isFiring(0.0f, 0.1f));
      assertFalse(processor.isFiring(0.1f, 0.0f));
    }

    @Test
    void shouldHandleNegativeValues() {
      assertTrue(processor.isFiring(-0.5f, -0.4f));
      assertFalse(processor.isFiring(-0.4f, -0.5f));
    }
  }

  // Tests for LessThanMetricOperatorProcessor
  @Nested
  class TestLessThanMetricOperatorProcessor {

    private LessThanMetricOperatorProcessor processor;

    @BeforeEach
    void setUp() {
      processor = new LessThanMetricOperatorProcessor();
    }

    @Test
    void shouldReturnTrueWhenActualValueIsLessThanThreshold() {
      assertTrue(processor.isFiring(0.5f, 0.4f));
    }

    @Test
    void shouldReturnFalseWhenActualValueIsGreaterThanThreshold() {
      assertFalse(processor.isFiring(0.5f, 0.6f));
    }

    @Test
    void shouldReturnFalseWhenActualValueEqualsThreshold() {
      assertFalse(processor.isFiring(0.5f, 0.5f));
    }

    @Test
    void shouldHandleZeroValues() {
      assertTrue(processor.isFiring(0.1f, 0.0f));
      assertFalse(processor.isFiring(0.0f, 0.1f));
    }

    @Test
    void shouldHandleNegativeValues() {
      assertTrue(processor.isFiring(-0.4f, -0.5f));
      assertFalse(processor.isFiring(-0.5f, -0.4f));
    }
  }

  // Tests for GreaterThanEqualToMetricOperatorProcessor
  @Nested
  class TestGreaterThanEqualToMetricOperatorProcessor {

    private GreaterThanEqualToMetricOperatorProcessor processor;

    @BeforeEach
    void setUp() {
      processor = new GreaterThanEqualToMetricOperatorProcessor();
    }

    @Test
    void shouldReturnTrueWhenActualValueIsGreaterThanThreshold() {
      assertTrue(processor.isFiring(0.5f, 0.6f));
    }

    @Test
    void shouldReturnTrueWhenActualValueEqualsThreshold() {
      assertTrue(processor.isFiring(0.5f, 0.5f));
    }

    @Test
    void shouldReturnFalseWhenActualValueIsLessThanThreshold() {
      assertFalse(processor.isFiring(0.5f, 0.4f));
    }

    @Test
    void shouldHandleZeroValues() {
      assertTrue(processor.isFiring(0.0f, 0.0f));
      assertTrue(processor.isFiring(0.0f, 0.1f));
      assertFalse(processor.isFiring(0.1f, 0.0f));
    }

    @Test
    void shouldHandleNegativeValues() {
      assertTrue(processor.isFiring(-0.5f, -0.5f));
      assertTrue(processor.isFiring(-0.5f, -0.4f));
      assertFalse(processor.isFiring(-0.4f, -0.5f));
    }
  }

  // Tests for LessThanEqualToMetricOperatorProcessor
  @Nested
  class TestLessThanEqualToMetricOperatorProcessor {

    private LessThanEqualToMetricOperatorProcessor processor;

    @BeforeEach
    void setUp() {
      processor = new LessThanEqualToMetricOperatorProcessor();
    }

    @Test
    void shouldReturnTrueWhenActualValueIsLessThanThreshold() {
      assertTrue(processor.isFiring(0.5f, 0.4f));
    }

    @Test
    void shouldReturnTrueWhenActualValueEqualsThreshold() {
      assertTrue(processor.isFiring(0.5f, 0.5f));
    }

    @Test
    void shouldReturnFalseWhenActualValueIsGreaterThanThreshold() {
      assertFalse(processor.isFiring(0.5f, 0.6f));
    }

    @Test
    void shouldHandleZeroValues() {
      assertTrue(processor.isFiring(0.0f, 0.0f));
      assertTrue(processor.isFiring(0.1f, 0.0f));
      assertFalse(processor.isFiring(0.0f, 0.1f));
    }

    @Test
    void shouldHandleNegativeValues() {
      assertTrue(processor.isFiring(-0.5f, -0.5f));
      assertTrue(processor.isFiring(-0.4f, -0.5f));
      assertFalse(processor.isFiring(-0.5f, -0.4f));
    }
  }
}

