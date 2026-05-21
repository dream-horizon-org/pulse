package org.dreamhorizon.pulseserver.errorgrouping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.errorgrouping.Symbolicator;
import org.dreamhorizon.pulseserver.errorgrouping.archive.StackTraceArchiveService;
import org.dreamhorizon.pulseserver.grouping.model.EventMeta;
import org.dreamhorizon.pulseserver.grouping.model.Frame;
import org.dreamhorizon.pulseserver.grouping.model.GroupingRules;
import org.dreamhorizon.pulseserver.util.serialization.ObjectMapperFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Focused tests for the rule-fetching wiring added to {@link ErrorGroupingService}.
 *
 * <p>This file deliberately lives alongside (not inside) the large legacy
 * {@code ErrorGroupingServiceTest} so the existing 80+ scenarios stay
 * untouched. New behaviours specific to {@link GroupingRuleService} integration
 * land here.</p>
 */
@ExtendWith(MockitoExtension.class)
class ErrorGroupingServiceRulesIntegrationTest {

  @Mock
  private ClickhouseQueryService clickhouseQueryService;

  @Mock
  private Symbolicator symbolicator;

  @Mock
  private StackTraceArchiveService stackTraceArchiveService;

  @Mock
  private GroupingRuleService groupingRuleService;

  private final ObjectMapper objectMapper = ObjectMapperFactory.get();

  private ErrorGroupingService service;

  @BeforeEach
  void setUp() {
    lenient().when(symbolicator.symbolicateIosNative(anyList(), any(EventMeta.class), any(), anyBoolean()))
        .thenAnswer(invocation -> {
          @SuppressWarnings("unchecked")
          List<Frame> frames = invocation.getArgument(0);
          return Single.just(frames.stream().map(Frame::getToken).toList());
        });
    service = new ErrorGroupingService(
        clickhouseQueryService,
        stackTraceArchiveService,
        symbolicator,
        objectMapper,
        groupingRuleService);
  }

  @Test
  void shouldFetchRulesForProjectBeforeGrouping() {
    when(groupingRuleService.getRules(eq("proj-42"), eq("com.dream11.example")))
        .thenReturn(Single.just(GroupingRules.builder()
            .inAppPrefix("com.dream11.")
            .build()));

    EventMeta meta = EventMeta.builder()
        .projectId("proj-42")
        .bundleId("com.dream11.example")
        .appVersion("1.0")
        .platform("Android")
        .build();

    String stackTrace = ""
        + "java.lang.NullPointerException: oops\n"
        + "\tat com.dream11.example.MyActivity.onCreate(MyActivity.java:42)\n";

    ErrorGroupingService.ProcessingResult result =
        service.processWithCompleteSymbolication(stackTrace, meta).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.group()).isNotNull();
    // Rules were fetched exactly once with both the projectId and bundleId of the event.
    verify(groupingRuleService, times(1)).getRules("proj-42", "com.dream11.example");
  }

  @Test
  void shouldFetchRulesEvenWhenStackTraceIsEmpty() {
    when(groupingRuleService.getRules(eq("proj-empty"), any()))
        .thenReturn(Single.just(GroupingRules.empty()));

    EventMeta meta = EventMeta.builder()
        .projectId("proj-empty")
        .platform("Android")
        .build();

    ErrorGroupingService.ProcessingResult result =
        service.processWithCompleteSymbolication("", meta).blockingGet();

    assertThat(result).isNotNull();
    // Even an empty trace still pulls rules — the upstream cache should be primed regardless.
    verify(groupingRuleService).getRules(eq("proj-empty"), any());
  }

  @Test
  void shouldPropagateRuleServiceErrorThroughProcessingResult() {
    RuntimeException ruleError = new RuntimeException("rule fetch failed");
    when(groupingRuleService.getRules(any(), any())).thenReturn(Single.error(ruleError));

    EventMeta meta = EventMeta.builder()
        .projectId("proj-broken")
        .bundleId("com.dream11.example")
        .platform("Android")
        .build();

    String stackTrace = ""
        + "java.lang.RuntimeException: boom\n"
        + "\tat com.dream11.example.X.y(X.java:1)\n";

    service.processWithCompleteSymbolication(stackTrace, meta).test()
        .assertError(throwable ->
            throwable == ruleError || throwable.getCause() == ruleError);
  }

  @Test
  void shouldThreadInAppPrefixIntoGroupingWhenRuleMatches() {
    // Build a rule bundle where the IN_APP prefix matches the top-of-stack package.
    GroupingRules rules = GroupingRules.builder()
        .inAppPrefix("com.dream11.")
        .build();
    when(groupingRuleService.getRules(any(), any())).thenReturn(Single.just(rules));

    EventMeta meta = EventMeta.builder()
        .projectId("proj-1")
        .bundleId("com.dream11.example")
        .platform("Android")
        .build();

    String stackTrace = ""
        + "java.lang.IllegalStateException: bad state\n"
        + "\tat com.dream11.foo.Bar.baz(Bar.java:10)\n"
        + "\tat android.os.Handler.dispatchMessage(Handler.java:99)\n";

    ErrorGroupingService.ProcessingResult result =
        service.processWithCompleteSymbolication(stackTrace, meta).blockingGet();

    assertThat(result.group()).isNotNull();
    // Signature must be non-empty when there's at least one classified IN_APP frame.
    assertThat(result.group().getSignature()).isNotNull().isNotBlank();
    assertThat(result.group().getGroupId()).isNotNull().isNotBlank();
  }
}
