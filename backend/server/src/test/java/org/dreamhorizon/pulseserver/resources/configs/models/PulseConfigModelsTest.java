package org.dreamhorizon.pulseserver.resources.configs.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.dreamhorizon.pulseserver.service.configs.models.Features;
import org.dreamhorizon.pulseserver.service.configs.models.FilterMode;
import org.dreamhorizon.pulseserver.service.configs.models.Scope;
import org.dreamhorizon.pulseserver.service.configs.models.Sdk;
import org.dreamhorizon.pulseserver.service.configs.models.rules;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PulseConfigModelsTest {

  // AttributeValue Tests - specifically for getType() and setType() methods
  @Nested
  class TestAttributeValue {

    @Test
    void shouldCreateWithNoArgs() {
      PulseConfig.AttributeValue attributeValue = new PulseConfig.AttributeValue();
      assertNotNull(attributeValue);
    }

    @Test
    void shouldCreateWithBuilder() {
      PulseConfig.AttributeValue attributeValue = PulseConfig.AttributeValue.builder()
          .name("testAttribute")
          .value("testValue")
          .build();

      assertEquals("testAttribute", attributeValue.getName());
      assertEquals("testValue", attributeValue.getValue());
    }

    @Test
    void shouldCreateWithAllArgsConstructor() {
      PulseConfig.AttributeValue attributeValue = new PulseConfig.AttributeValue(
          "attributeName", "attributeValue");

      assertEquals("attributeName", attributeValue.getName());
      assertEquals("attributeValue", attributeValue.getValue());
    }

    @Test
    void shouldSetAndGetName() {
      PulseConfig.AttributeValue attributeValue = new PulseConfig.AttributeValue();

      attributeValue.setName("newName");

      assertEquals("newName", attributeValue.getName());
    }

    @Test
    void shouldSetAndGetValue() {
      PulseConfig.AttributeValue attributeValue = new PulseConfig.AttributeValue();

      attributeValue.setValue("newValue");

      assertEquals("newValue", attributeValue.getValue());
    }

    @Test
    void shouldAlwaysReturnStringForGetType() {
      PulseConfig.AttributeValue attributeValue = new PulseConfig.AttributeValue();

      // getType() should always return "string" regardless of any other state
      assertEquals("string", attributeValue.getType());
    }

    @Test
    void shouldReturnStringTypeAfterSettingDifferentType() {
      PulseConfig.AttributeValue attributeValue = new PulseConfig.AttributeValue();

      // Call setType with a different value
      attributeValue.setType("integer");

      // getType() should still return "string" - the setType is a no-op
      assertEquals("string", attributeValue.getType());
    }

    @Test
    void shouldReturnStringTypeAfterSettingNullType() {
      PulseConfig.AttributeValue attributeValue = new PulseConfig.AttributeValue();

      // Call setType with null
      attributeValue.setType(null);

      // getType() should still return "string"
      assertEquals("string", attributeValue.getType());
    }

    @Test
    void shouldReturnStringTypeAfterSettingEmptyType() {
      PulseConfig.AttributeValue attributeValue = new PulseConfig.AttributeValue();

      // Call setType with empty string
      attributeValue.setType("");

      // getType() should still return "string"
      assertEquals("string", attributeValue.getType());
    }

    @Test
    void shouldReturnStringTypeAfterMultipleSetTypeCalls() {
      PulseConfig.AttributeValue attributeValue = new PulseConfig.AttributeValue();

      // Call setType multiple times with different values
      attributeValue.setType("boolean");
      attributeValue.setType("number");
      attributeValue.setType("object");

      // getType() should still return "string"
      assertEquals("string", attributeValue.getType());
    }

    @Test
    void shouldReturnStringTypeEvenWhenBuilderUsed() {
      PulseConfig.AttributeValue attributeValue = PulseConfig.AttributeValue.builder()
          .name("attr")
          .value("val")
          .build();

      // getType() should return "string"
      assertEquals("string", attributeValue.getType());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      PulseConfig.AttributeValue attr1 = PulseConfig.AttributeValue.builder()
          .name("testName")
          .value("testValue")
          .build();
      PulseConfig.AttributeValue attr2 = PulseConfig.AttributeValue.builder()
          .name("testName")
          .value("testValue")
          .build();

      assertEquals(attr1, attr2);
      assertEquals(attr1.hashCode(), attr2.hashCode());
    }

    @Test
    void shouldHaveCorrectToString() {
      PulseConfig.AttributeValue attributeValue = PulseConfig.AttributeValue.builder()
          .name("attr")
          .value("val")
          .build();

      String toString = attributeValue.toString();
      assertNotNull(toString);
      assertTrue(toString.contains("attr"));
      assertTrue(toString.contains("val"));
    }
  }

  // AttributeToAdd Tests
  @Nested
  class TestAttributeToAdd {

    @Test
    void shouldCreateWithNoArgs() {
      PulseConfig.AttributeToAdd attributeToAdd = new PulseConfig.AttributeToAdd();
      assertNotNull(attributeToAdd);
    }

    @Test
    void shouldCreateWithBuilder() {
      List<PulseConfig.AttributeValue> values = Arrays.asList(
          PulseConfig.AttributeValue.builder().name("attr1").value("val1").build(),
          PulseConfig.AttributeValue.builder().name("attr2").value("val2").build()
      );
      PulseConfig.EventFilter condition = PulseConfig.EventFilter.builder()
          .name("eventName")
          .build();

      PulseConfig.AttributeToAdd attributeToAdd = PulseConfig.AttributeToAdd.builder()
          .values(values)
          .condition(condition)
          .build();

      assertEquals(values, attributeToAdd.getValues());
      assertEquals(condition, attributeToAdd.getCondition());
    }

    @Test
    void shouldSetAndGetValues() {
      PulseConfig.AttributeToAdd attributeToAdd = new PulseConfig.AttributeToAdd();
      List<PulseConfig.AttributeValue> values = new ArrayList<>();

      attributeToAdd.setValues(values);

      assertEquals(values, attributeToAdd.getValues());
    }

    @Test
    void shouldSetAndGetCondition() {
      PulseConfig.AttributeToAdd attributeToAdd = new PulseConfig.AttributeToAdd();
      PulseConfig.EventFilter condition = PulseConfig.EventFilter.builder()
          .name("test")
          .build();

      attributeToAdd.setCondition(condition);

      assertEquals(condition, attributeToAdd.getCondition());
    }

    @Test
    void shouldVerifyTypeFieldInAttributeValues() {
      PulseConfig.AttributeValue attr1 = PulseConfig.AttributeValue.builder()
          .name("environment")
          .value("production")
          .build();
      PulseConfig.AttributeValue attr2 = PulseConfig.AttributeValue.builder()
          .name("team")
          .value("backend")
          .build();

      List<PulseConfig.AttributeValue> values = Arrays.asList(attr1, attr2);
      PulseConfig.AttributeToAdd attributeToAdd = PulseConfig.AttributeToAdd.builder()
          .values(values)
          .build();

      // Verify each AttributeValue in the list returns "string" for type
      for (PulseConfig.AttributeValue value : attributeToAdd.getValues()) {
        assertEquals("string", value.getType());
      }
    }
  }

  // EventFilter Tests
  @Nested
  class TestEventFilter {

    @Test
    void shouldCreateWithNoArgs() {
      PulseConfig.EventFilter eventFilter = new PulseConfig.EventFilter();
      assertNotNull(eventFilter);
    }

    @Test
    void shouldCreateWithBuilder() {
      List<PulseConfig.EventPropMatch> props = new ArrayList<>();
      List<Scope> scopes = Arrays.asList(Scope.logs, Scope.traces);
      List<Sdk> sdks = Arrays.asList(Sdk.android_java);

      PulseConfig.EventFilter eventFilter = PulseConfig.EventFilter.builder()
          .name("testEvent")
          .props(props)
          .scopes(scopes)
          .sdks(sdks)
          .build();

      assertEquals("testEvent", eventFilter.getName());
      assertEquals(props, eventFilter.getProps());
      assertEquals(scopes, eventFilter.getScopes());
      assertEquals(sdks, eventFilter.getSdks());
    }

    @Test
    void shouldSetAndGetAllFields() {
      PulseConfig.EventFilter eventFilter = new PulseConfig.EventFilter();

      eventFilter.setName("eventName");
      eventFilter.setProps(new ArrayList<>());
      eventFilter.setScopes(Arrays.asList(Scope.metrics));
      eventFilter.setSdks(Arrays.asList(Sdk.ios_native));

      assertEquals("eventName", eventFilter.getName());
      assertNotNull(eventFilter.getProps());
      assertEquals(1, eventFilter.getScopes().size());
      assertEquals(1, eventFilter.getSdks().size());
    }
  }

  // EventPropMatch Tests
  @Nested
  class TestEventPropMatch {

    @Test
    void shouldCreateWithNoArgs() {
      PulseConfig.EventPropMatch propMatch = new PulseConfig.EventPropMatch();
      assertNotNull(propMatch);
    }

    @Test
    void shouldCreateWithBuilder() {
      PulseConfig.EventPropMatch propMatch = PulseConfig.EventPropMatch.builder()
          .name("propName")
          .value("propValue.*")
          .build();

      assertEquals("propName", propMatch.getName());
      assertEquals("propValue.*", propMatch.getValue());
    }

    @Test
    void shouldSetAndGetFields() {
      PulseConfig.EventPropMatch propMatch = new PulseConfig.EventPropMatch();

      propMatch.setName("testProp");
      propMatch.setValue("testRegex");

      assertEquals("testProp", propMatch.getName());
      assertEquals("testRegex", propMatch.getValue());
    }
  }

  // SamplingConfig Tests
  @Nested
  class TestSamplingConfig {

    @Test
    void shouldCreateWithNoArgs() {
      PulseConfig.SamplingConfig samplingConfig = new PulseConfig.SamplingConfig();
      assertNotNull(samplingConfig);
    }

    @Test
    void shouldCreateWithBuilder() {
      PulseConfig.DefaultSampling defaultSampling = PulseConfig.DefaultSampling.builder()
          .sessionSampleRate(0.5)
          .build();
      List<PulseConfig.SamplingRule> rules = new ArrayList<>();

      PulseConfig.SamplingConfig samplingConfig = PulseConfig.SamplingConfig.builder()
          .defaultSampling(defaultSampling)
          .rules(rules)
          .build();

      assertEquals(defaultSampling, samplingConfig.getDefaultSampling());
      assertEquals(rules, samplingConfig.getRules());
    }
  }

  // DefaultSampling Tests
  @Nested
  class TestDefaultSampling {

    @Test
    void shouldCreateWithNoArgs() {
      PulseConfig.DefaultSampling defaultSampling = new PulseConfig.DefaultSampling();
      assertNotNull(defaultSampling);
    }

    @Test
    void shouldCreateWithBuilder() {
      PulseConfig.DefaultSampling defaultSampling = PulseConfig.DefaultSampling.builder()
          .sessionSampleRate(0.75)
          .build();

      assertEquals(0.75, defaultSampling.getSessionSampleRate());
    }

    @Test
    void shouldSetAndGetSessionSampleRate() {
      PulseConfig.DefaultSampling defaultSampling = new PulseConfig.DefaultSampling();

      defaultSampling.setSessionSampleRate(0.9);

      assertEquals(0.9, defaultSampling.getSessionSampleRate());
    }
  }

  // SamplingRule Tests
  @Nested
  class TestSamplingRule {

    @Test
    void shouldCreateWithNoArgs() {
      PulseConfig.SamplingRule samplingRule = new PulseConfig.SamplingRule();
      assertNotNull(samplingRule);
    }

    @Test
    void shouldCreateWithBuilder() {
      PulseConfig.SamplingRule samplingRule = PulseConfig.SamplingRule.builder()
          .name(rules.os_version)
          .sdks(Arrays.asList(Sdk.android_java))
          .value("14")
          .sessionSampleRate(1.0)
          .build();

      assertEquals(rules.os_version, samplingRule.getName());
      assertEquals("14", samplingRule.getValue());
      assertEquals(1.0, samplingRule.getSessionSampleRate());
    }

    @Test
    void shouldSetAndGetAllFields() {
      PulseConfig.SamplingRule samplingRule = new PulseConfig.SamplingRule();

      samplingRule.setName(rules.app_version);
      samplingRule.setSdks(Arrays.asList(Sdk.ios_native));
      samplingRule.setValue("2.0.0");
      samplingRule.setSessionSampleRate(0.8);

      assertEquals(rules.app_version, samplingRule.getName());
      assertEquals("2.0.0", samplingRule.getValue());
      assertEquals(0.8, samplingRule.getSessionSampleRate());
    }
  }

  // CriticalEventPolicies Tests
  @Nested
  class TestCriticalEventPolicies {

    @Test
    void shouldCreateWithNoArgs() {
      PulseConfig.CriticalEventPolicies policies = new PulseConfig.CriticalEventPolicies();
      assertNotNull(policies);
    }

    @Test
    void shouldCreateWithBuilder() {
      List<PulseConfig.CriticalPolicyRule> alwaysSend = new ArrayList<>();
      PulseConfig.CriticalEventPolicies policies = PulseConfig.CriticalEventPolicies.builder()
          .alwaysSend(alwaysSend)
          .build();

      assertEquals(alwaysSend, policies.getAlwaysSend());
    }
  }

  // CriticalSessionPolicies Tests
  @Nested
  class TestCriticalSessionPolicies {

    @Test
    void shouldCreateWithNoArgs() {
      PulseConfig.CriticalSessionPolicies policies = new PulseConfig.CriticalSessionPolicies();
      assertNotNull(policies);
    }

    @Test
    void shouldCreateWithBuilder() {
      List<PulseConfig.CriticalPolicyRule> alwaysSend = new ArrayList<>();
      PulseConfig.CriticalSessionPolicies policies = PulseConfig.CriticalSessionPolicies.builder()
          .alwaysSend(alwaysSend)
          .build();

      assertEquals(alwaysSend, policies.getAlwaysSend());
    }
  }

  // CriticalPolicyRule Tests
  @Nested
  class TestCriticalPolicyRule {

    @Test
    void shouldCreateWithNoArgs() {
      PulseConfig.CriticalPolicyRule rule = new PulseConfig.CriticalPolicyRule();
      assertNotNull(rule);
    }

    @Test
    void shouldCreateWithBuilder() {
      List<PulseConfig.EventPropMatch> props = new ArrayList<>();
      List<Scope> scopes = Arrays.asList(Scope.logs);
      List<Sdk> sdks = Arrays.asList(Sdk.android_java);

      PulseConfig.CriticalPolicyRule rule = PulseConfig.CriticalPolicyRule.builder()
          .name("crashEvent")
          .props(props)
          .scopes(scopes)
          .sdks(sdks)
          .build();

      assertEquals("crashEvent", rule.getName());
      assertEquals(props, rule.getProps());
      assertEquals(scopes, rule.getScopes());
      assertEquals(sdks, rule.getSdks());
    }
  }

  // FilterConfig Tests
  @Nested
  class TestFilterConfig {

    @Test
    void shouldCreateWithNoArgs() {
      PulseConfig.FilterConfig filterConfig = new PulseConfig.FilterConfig();
      assertNotNull(filterConfig);
    }

    @Test
    void shouldCreateWithBuilder() {
      List<PulseConfig.EventFilter> values = new ArrayList<>();

      PulseConfig.FilterConfig filterConfig = PulseConfig.FilterConfig.builder()
          .mode(FilterMode.blacklist)
          .values(values)
          .build();

      assertEquals(FilterMode.blacklist, filterConfig.getMode());
      assertEquals(values, filterConfig.getValues());
    }

    @Test
    void shouldSetAndGetFields() {
      PulseConfig.FilterConfig filterConfig = new PulseConfig.FilterConfig();

      filterConfig.setMode(FilterMode.whitelist);
      filterConfig.setValues(new ArrayList<>());

      assertEquals(FilterMode.whitelist, filterConfig.getMode());
      assertNotNull(filterConfig.getValues());
    }
  }

  // SignalsConfig Tests
  @Nested
  class TestSignalsConfig {

    @Test
    void shouldCreateWithNoArgs() {
      PulseConfig.SignalsConfig signalsConfig = new PulseConfig.SignalsConfig();
      assertNotNull(signalsConfig);
    }

    @Test
    void shouldCreateWithBuilder() {
      PulseConfig.FilterConfig filters = PulseConfig.FilterConfig.builder()
          .mode(FilterMode.blacklist)
          .build();
      List<PulseConfig.EventFilter> attributesToDrop = new ArrayList<>();
      List<PulseConfig.AttributeToAdd> attributesToAdd = new ArrayList<>();

      PulseConfig.SignalsConfig signalsConfig = PulseConfig.SignalsConfig.builder()
          .scheduleDurationMs(5000)
          .logsCollectorUrl("http://logs.example.com")
          .metricCollectorUrl("http://metrics.example.com")
          .spanCollectorUrl("http://spans.example.com")
          .filters(filters)
          .attributesToDrop(attributesToDrop)
          .attributesToAdd(attributesToAdd)
          .build();

      assertEquals(5000, signalsConfig.getScheduleDurationMs());
      assertEquals("http://logs.example.com", signalsConfig.getLogsCollectorUrl());
      assertEquals("http://metrics.example.com", signalsConfig.getMetricCollectorUrl());
      assertEquals("http://spans.example.com", signalsConfig.getSpanCollectorUrl());
      assertEquals(filters, signalsConfig.getFilters());
      assertEquals(attributesToDrop, signalsConfig.getAttributesToDrop());
      assertEquals(attributesToAdd, signalsConfig.getAttributesToAdd());
    }

    @Test
    void shouldSetAndGetAllFields() {
      PulseConfig.SignalsConfig signalsConfig = new PulseConfig.SignalsConfig();

      signalsConfig.setScheduleDurationMs(10000);
      signalsConfig.setLogsCollectorUrl("http://new-logs.example.com");
      signalsConfig.setMetricCollectorUrl("http://new-metrics.example.com");
      signalsConfig.setSpanCollectorUrl("http://new-spans.example.com");
      signalsConfig.setFilters(new PulseConfig.FilterConfig());
      signalsConfig.setAttributesToDrop(new ArrayList<>());
      signalsConfig.setAttributesToAdd(new ArrayList<>());

      assertEquals(10000, signalsConfig.getScheduleDurationMs());
      assertEquals("http://new-logs.example.com", signalsConfig.getLogsCollectorUrl());
      assertEquals("http://new-metrics.example.com", signalsConfig.getMetricCollectorUrl());
      assertEquals("http://new-spans.example.com", signalsConfig.getSpanCollectorUrl());
    }
  }

  // InteractionConfig Tests
  @Nested
  class TestInteractionConfig {

    @Test
    void shouldCreateWithNoArgs() {
      PulseConfig.InteractionConfig interactionConfig = new PulseConfig.InteractionConfig();
      assertNotNull(interactionConfig);
    }

    @Test
    void shouldCreateWithBuilder() {
      PulseConfig.InteractionConfig interactionConfig = PulseConfig.InteractionConfig.builder()
          .collectorUrl("http://collector.example.com")
          .configUrl("http://config.example.com")
          .beforeInitQueueSize(100)
          .build();

      assertEquals("http://collector.example.com", interactionConfig.getCollectorUrl());
      assertEquals("http://config.example.com", interactionConfig.getConfigUrl());
      assertEquals(100, interactionConfig.getBeforeInitQueueSize());
    }

    @Test
    void shouldSetAndGetAllFields() {
      PulseConfig.InteractionConfig interactionConfig = new PulseConfig.InteractionConfig();

      interactionConfig.setCollectorUrl("http://new-collector.example.com");
      interactionConfig.setConfigUrl("http://new-config.example.com");
      interactionConfig.setBeforeInitQueueSize(200);

      assertEquals("http://new-collector.example.com", interactionConfig.getCollectorUrl());
      assertEquals("http://new-config.example.com", interactionConfig.getConfigUrl());
      assertEquals(200, interactionConfig.getBeforeInitQueueSize());
    }
  }

  // FeatureConfig Tests
  @Nested
  class TestFeatureConfig {

    @Test
    void shouldCreateWithNoArgs() {
      PulseConfig.FeatureConfig featureConfig = new PulseConfig.FeatureConfig();
      assertNotNull(featureConfig);
    }

    @Test
    void shouldCreateWithBuilder() {
      PulseConfig.FeatureConfig featureConfig = PulseConfig.FeatureConfig.builder()
          .featureName(Features.java_crash)
          .sessionSampleRate(1.0)
          .sdks(Arrays.asList(Sdk.android_java))
          .build();

      assertEquals(Features.java_crash, featureConfig.getFeatureName());
      assertEquals(1.0, featureConfig.getSessionSampleRate());
      assertEquals(1, featureConfig.getSdks().size());
    }

    @Test
    void shouldSetAndGetAllFields() {
      PulseConfig.FeatureConfig featureConfig = new PulseConfig.FeatureConfig();

      featureConfig.setFeatureName(Features.java_anr);
      featureConfig.setSessionSampleRate(0.5);
      featureConfig.setSdks(Arrays.asList(Sdk.android_rn, Sdk.ios_rn));

      assertEquals(Features.java_anr, featureConfig.getFeatureName());
      assertEquals(0.5, featureConfig.getSessionSampleRate());
      assertEquals(2, featureConfig.getSdks().size());
    }
  }

  // PulseConfig (main class) Tests
  @Nested
  class TestPulseConfig {

    @Test
    void shouldCreateWithNoArgs() {
      PulseConfig pulseConfig = new PulseConfig();
      assertNotNull(pulseConfig);
    }

    @Test
    void shouldCreateWithBuilder() {
      PulseConfig.SamplingConfig sampling = PulseConfig.SamplingConfig.builder().build();
      PulseConfig.SignalsConfig signals = PulseConfig.SignalsConfig.builder().build();
      PulseConfig.InteractionConfig interaction = PulseConfig.InteractionConfig.builder().build();
      List<PulseConfig.FeatureConfig> features = new ArrayList<>();

      PulseConfig pulseConfig = PulseConfig.builder()
          .version(1L)
          .description("Test Config")
          .sampling(sampling)
          .signals(signals)
          .interaction(interaction)
          .features(features)
          .build();

      assertEquals(1L, pulseConfig.getVersion());
      assertEquals("Test Config", pulseConfig.getDescription());
      assertEquals(sampling, pulseConfig.getSampling());
      assertEquals(signals, pulseConfig.getSignals());
      assertEquals(interaction, pulseConfig.getInteraction());
      assertEquals(features, pulseConfig.getFeatures());
    }

    @Test
    void shouldSetAndGetAllFields() {
      PulseConfig pulseConfig = new PulseConfig();

      pulseConfig.setVersion(2L);
      pulseConfig.setDescription("Updated Config");
      pulseConfig.setSampling(new PulseConfig.SamplingConfig());
      pulseConfig.setSignals(new PulseConfig.SignalsConfig());
      pulseConfig.setInteraction(new PulseConfig.InteractionConfig());
      pulseConfig.setFeatures(new ArrayList<>());

      assertEquals(2L, pulseConfig.getVersion());
      assertEquals("Updated Config", pulseConfig.getDescription());
      assertNotNull(pulseConfig.getSampling());
      assertNotNull(pulseConfig.getSignals());
      assertNotNull(pulseConfig.getInteraction());
      assertNotNull(pulseConfig.getFeatures());
    }

    @Test
    void shouldHaveCorrectEqualsAndHashCode() {
      PulseConfig config1 = PulseConfig.builder()
          .version(1L)
          .description("Test")
          .build();
      PulseConfig config2 = PulseConfig.builder()
          .version(1L)
          .description("Test")
          .build();

      assertEquals(config1, config2);
      assertEquals(config1.hashCode(), config2.hashCode());
    }
  }
}

