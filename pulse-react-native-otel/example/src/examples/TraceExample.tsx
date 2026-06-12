import { useState } from 'react';
import { View, Text, Button, StyleSheet, ScrollView } from 'react-native';
import { Pulse, SpanStatusCode } from '@dreamhorizonorg/pulse-react-native';
import { discardSpan } from '../../../src/trace';

const TAG = 'Pulse Trace Demo';
const TEST_TAG = '[Inactive Span Test]';
const NESTED_TEST_TAG = '[Nested Cancellation Test]';

// Test inactive span behavior
const testInactiveSpan = async () => {
  try {
    const p1 = Pulse.startSpan('parent_active', {
      attributes: { level: 'parent', type: 'active' },
    });

    const c1 = Pulse.startSpan('child_inactive', {
      attributes: { level: 'child', type: 'inactive' },
      inheritContext: false, // Don't inherit context (independent span)
    });

    const c2 = Pulse.startSpan('child_active', {
      attributes: { level: 'child', type: 'active' },
    });

    c2.end();
    c1.end();
    p1.end();
    console.log(`${TEST_TAG} Test complete`);
  } catch (error) {
    console.error(`${TEST_TAG} Error:`, error);
  }
};

// Test nested span cancellation
const testNestedSpanCancellation = async () => {
  let p1: ReturnType<typeof Pulse.startSpan> | null = null;
  let c1: ReturnType<typeof Pulse.startSpan> | null = null;
  let g1: ReturnType<typeof Pulse.startSpan> | null = null;

  try {
    p1 = Pulse.startSpan('parent_p1', {
      attributes: { level: 'parent', test: 'nested_cancellation' },
    });

    c1 = Pulse.startSpan('child_c1', {
      attributes: { level: 'child', operation: 'child_operation' },
      inheritContext: false, // Don't inherit context (independent span)
    });

    g1 = Pulse.startSpan('grandchild_g1', {
      attributes: { level: 'grandchild', operation: 'grandchild_operation' },
    });

    // Discard C1 span (cleanup without sending to backend)
    if (c1?.spanId) {
      discardSpan(c1.spanId);
    }
    c1 = null;

    g1?.end();
    p1?.end();
    console.log(`${NESTED_TEST_TAG} Test complete`);
  } catch (error) {
    console.error(`${NESTED_TEST_TAG} Error:`, error);
  }
};

const createNestedSpans = async () => {
  const parentSpan = Pulse.startSpan('parent_operation', {
    attributes: {
      operation: 'data_processing',
      user_id: '12345',
      batch_size: 100,
    },
  });

  await new Promise((resolve) => setTimeout(resolve, 200));

  const child1Span = Pulse.startSpan('child_fetch_data', {
    attributes: {
      source: 'database',
      table: 'users',
    },
  });

  child1Span.addEvent('query_executed', { query_time_ms: 45 });
  await new Promise((resolve) => setTimeout(resolve, 150));
  child1Span.setAttributes({ rows_fetched: 100 });
  child1Span.end();

  await new Promise((resolve) => setTimeout(resolve, 100));

  const child2Span = Pulse.startSpan('child_process_data', {
    attributes: {
      processor: 'transformer',
      format: 'json',
    },
  });

  const grandchildSpan = Pulse.startSpan('grandchild_validate', {
    attributes: {
      validator: 'schema_validator',
      rules: 5,
    },
  });

  grandchildSpan.addEvent('validation_started');
  await new Promise((resolve) => setTimeout(resolve, 100));
  grandchildSpan.setAttributes({ valid: true, errors: 0 });
  grandchildSpan.end();

  child2Span.addEvent('transformation_complete', { items_processed: 100 });
  await new Promise((resolve) => setTimeout(resolve, 100));
  child2Span.end();

  await new Promise((resolve) => setTimeout(resolve, 100));

  const child3Span = Pulse.startSpan('child_save_results', {
    attributes: {
      destination: 'cache',
      ttl: 3600,
    },
  });

  await new Promise((resolve) => setTimeout(resolve, 120));
  child3Span.setAttributes({ saved: true, cache_hit: false });
  child3Span.end();

  parentSpan.addEvent('all_children_completed', { total_time_ms: 570 });
  await new Promise((resolve) => setTimeout(resolve, 50));

  parentSpan.end();
  console.log(`${TAG} nested spans demo complete`);
};

export default function TraceDemo() {
  const [manualSpan, setManualSpan] = useState<ReturnType<
    typeof Pulse.startSpan
  > | null>(null);
  const [syncResult, setSyncResult] = useState<number | null>(null);
  const [asyncNoReturnDone, setAsyncNoReturnDone] = useState<boolean>(false);
  const [asyncResult, setAsyncResult] = useState<string | null>(null);

  return (
    <View style={styles.fullContainer}>
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.title}>Trace & Span Demo</Text>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Auto-end span</Text>
          <Button
            title="Run auto-end span (sync with return)"
            onPress={() => {
              const value = Pulse.trackSpan(
                'auto_sync_return',
                { attributes: { kind: 'sync' } },
                () => {
                  return 42;
                }
              ) as number;
              setSyncResult(value);
            }}
          />
          <View style={styles.space} />
          <Button
            title="Run auto-end span (async without return)"
            onPress={async () => {
              await Pulse.trackSpan(
                'auto_async_no_return',
                { attributes: { kind: 'async' } },
                async () => {
                  await new Promise((r) => setTimeout(r, 150));
                }
              );
              setAsyncNoReturnDone(true);
            }}
            color="#4CAF50"
          />
          <View style={styles.space} />
          <Button
            title="Run auto-end span (async with return)"
            onPress={async () => {
              const result = (await Pulse.trackSpan(
                'auto_async_return',
                { attributes: { kind: 'async' } },
                async () => {
                  await new Promise((r) => setTimeout(r, 150));
                  return 'completed';
                }
              )) as string;
              setAsyncResult(result);
            }}
            color="#2196F3"
          />
          <View style={styles.space} />
          <View style={styles.resultBox}>
            <Text style={styles.resultText}>
              Sync return: {syncResult ?? 'N/A'}
            </Text>
            <Text style={styles.resultText}>
              Async (no return): {asyncNoReturnDone ? 'done' : 'pending'}
            </Text>
            <Text style={styles.resultText}>
              Async return: {asyncResult ?? 'N/A'}
            </Text>
          </View>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Manual span</Text>
          <Button
            title={
              manualSpan ? 'Manual span already started' : 'Start manual span'
            }
            onPress={() => {
              if (manualSpan) return;
              const span = Pulse.startSpan('manual_span', {
                attributes: { phase: 'start' },
              });
              setManualSpan(span);
            }}
          />
          <View style={styles.space} />
          <Button
            title="Add event to manual span"
            onPress={() =>
              manualSpan &&
              manualSpan.addEvent('manual_event', { ts: Date.now() })
            }
            color="#607D8B"
          />
          <View style={styles.space} />
          <Button
            title="Set attributes on manual span"
            onPress={() =>
              manualSpan &&
              manualSpan.setAttributes({ tries: 2, cached: false })
            }
            color="#795548"
          />
          <View style={styles.space} />
          <Button
            title="Record exception on manual span"
            onPress={() =>
              manualSpan && manualSpan.recordException(new Error('ManualError'))
            }
            color="#9E9E9E"
          />
          <View style={styles.space} />
          <Button
            title="End manual span (OK)"
            onPress={() => {
              if (!manualSpan) return;
              manualSpan.end(SpanStatusCode.OK);
              setManualSpan(null);
            }}
            color="#4CAF50"
          />
          <View style={styles.space} />
          <Button
            title="End manual span (ERROR)"
            onPress={() => {
              if (!manualSpan) return;
              manualSpan.end(SpanStatusCode.ERROR);
              setManualSpan(null);
            }}
            color="#F44336"
          />
          <View style={styles.space} />
          <Button
            title="End manual span (UNSET)"
            onPress={() => {
              if (!manualSpan) return;
              manualSpan.end(SpanStatusCode.UNSET);
              setManualSpan(null);
            }}
            color="#3F51B5"
          />
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Nested Spans Demo</Text>
          <Text style={styles.description}>
            Create a parent span with multiple child spans to see the hierarchy
          </Text>
          <View style={styles.space} />
          <Button
            title="Create Nested Spans"
            onPress={createNestedSpans}
            color="#FF9800"
          />
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Inactive Span Test</Text>
          <Text style={styles.description}>
            Test that inactive spans don't become children of active spans
          </Text>
          <View style={styles.space} />
          <Button
            title="Test Inactive Span"
            onPress={testInactiveSpan}
            color="#9C27B0"
          />
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Nested Cancellation Test</Text>
          <Text style={styles.description}>
            Test nested span cancellation: P1 → C1 (inactive) → G1, cancel C1
          </Text>
          <View style={styles.space} />
          <Button
            title="Test Nested Cancellation"
            onPress={testNestedSpanCancellation}
            color="#E91E63"
          />
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  fullContainer: { flex: 1 },
  backButton: {
    padding: 15,
    backgroundColor: '#f0f0f0',
    borderBottomWidth: 1,
    borderBottomColor: '#ddd',
  },
  backButtonText: {
    fontSize: 16,
    color: '#2196F3',
    fontWeight: '600',
  },
  container: {
    padding: 20,
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 20,
    textAlign: 'center',
  },
  section: {
    marginVertical: 14,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 8,
    textAlign: 'center',
  },
  space: { height: 10 },
  resultBox: { marginTop: 10 },
  resultText: { fontSize: 12, color: '#555', textAlign: 'center' },
  description: {
    fontSize: 12,
    color: '#666',
    marginBottom: 8,
    textAlign: 'center',
    fontStyle: 'italic',
  },
});
