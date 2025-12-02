import React from 'react';
import { View, Text, Button, StyleSheet, ScrollView } from 'react-native';
import { Pulse, SpanStatusCode } from '@dreamhorizonorg/pulse-react-native';

const TAG = 'Pulse Trace Demo';
const createNestedSpans = async () => {
    console.log(`${TAG} Starting nested spans demo...`);
    
    // Start parent span
    const parentSpan = Pulse.startSpan('parent_operation', {
      attributes: {
        operation: 'data_processing',
        user_id: '12345',
        batch_size: 100
      }
    });
    console.log(`${TAG} Parent span started:`, parentSpan.spanId);
    
    // Simulate some work
    await new Promise(resolve => setTimeout(resolve, 200));
    
    // Create first child span
    const child1Span = Pulse.startSpan('child_fetch_data', {
      attributes: {
        source: 'database',
        table: 'users'
      }
    });
    console.log(`${TAG} Child 1 span started:`, child1Span.spanId);
    
    child1Span.addEvent('query_executed', { query_time_ms: 45 });
    await new Promise(resolve => setTimeout(resolve, 150));
    child1Span.setAttributes({ rows_fetched: 100 });
    child1Span.end();
    console.log(`${TAG} Child 1 span ended`);
    
    // Simulate some gap between child spans
    await new Promise(resolve => setTimeout(resolve, 100));

    // Create second child span
    const child2Span = Pulse.startSpan('child_process_data', {
      attributes: {
        processor: 'transformer',
        format: 'json'
      }
    });
    console.log(`${TAG} Child 2 span started:`, child2Span.spanId);
    
    // Create a grandchild span (nested deeper)
    const grandchildSpan = Pulse.startSpan('grandchild_validate', {
      attributes: {
        validator: 'schema_validator',
        rules: 5
      }
    });
    console.log(`${TAG} Grandchild span started:`, grandchildSpan.spanId);
    
    grandchildSpan.addEvent('validation_started');
    await new Promise(resolve => setTimeout(resolve, 100));
    grandchildSpan.setAttributes({ valid: true, errors: 0 });
    grandchildSpan.end();
    console.log(`${TAG} Grandchild span ended`);
    
    child2Span.addEvent('transformation_complete', { items_processed: 100 });
    await new Promise(resolve => setTimeout(resolve, 100));
    child2Span.end();
    console.log(`${TAG} Child 2 span ended`);
    
    // Simulate some gap between child spans
    await new Promise(resolve => setTimeout(resolve, 100));
    
    // Create third child span
    const child3Span = Pulse.startSpan('child_save_results', {
      attributes: {
        destination: 'cache',
        ttl: 3600
      }
    });
    console.log(`${TAG} Child 3 span started:`, child3Span.spanId);
    
    await new Promise(resolve => setTimeout(resolve, 120));
    child3Span.setAttributes({ saved: true, cache_hit: false });
    child3Span.end();
    console.log(`${TAG} Child 3 span ended`);
    
    // Add final event to parent
    parentSpan.addEvent('all_children_completed', { total_time_ms: 570 });
    await new Promise(resolve => setTimeout(resolve, 50));
    
    // End parent span
    parentSpan.end();
    console.log(`${TAG} Parent span ended - nested spans demo complete`);
    
    alert('Nested spans created! Check your telemetry backend to see the hierarchy:\n\n' +
          'parent_operation\n' +
          '  ├─ child_fetch_data\n' +
          '  ├─ child_process_data\n' +
          '  │   └─ grandchild_validate\n' +
          '  └─ child_save_results');
};

export default function TraceDemo() {
  const [manualSpan, setManualSpan] = React.useState<ReturnType<typeof Pulse.startSpan> | null>(null);
  const [syncResult, setSyncResult] = React.useState<number | null>(null);
  const [asyncNoReturnDone, setAsyncNoReturnDone] = React.useState<boolean>(false);
  const [asyncResult, setAsyncResult] = React.useState<string | null>(null);

  return (
    <View style={styles.fullContainer}>
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.title}>Trace & Span Demo</Text>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Auto-end span</Text>
          <Button
            title="Run auto-end span (sync with return)"
            onPress={() => {
              const value = Pulse.trackSpan('auto_sync_return', { attributes: { kind: 'sync' } }, () => {
                return 42;
              }) as number;
              setSyncResult(value);
            }}
          />
          <View style={styles.space} />
          <Button
            title="Run auto-end span (async without return)"
            onPress={async () => {
              await Pulse.trackSpan('auto_async_no_return', { attributes: { kind: 'async' } }, async () => {
                await new Promise((r) => setTimeout(r, 150));
              });
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
            <Text style={styles.resultText}>Sync return: {syncResult ?? 'N/A'}</Text>
            <Text style={styles.resultText}>Async (no return): {asyncNoReturnDone ? 'done' : 'pending'}</Text>
            <Text style={styles.resultText}>Async return: {asyncResult ?? 'N/A'}</Text>
          </View>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Manual span</Text>
          <Button
            title={manualSpan ? 'Manual span already started' : 'Start manual span'}
            onPress={() => {
              if (manualSpan) return;
              const span = Pulse.startSpan('manual_span', { attributes: { phase: 'start' } });
              setManualSpan(span);
            }}
          />
          <View style={styles.space} />
          <Button
            title="Add event to manual span"
            onPress={() => manualSpan && manualSpan.addEvent('manual_event', { ts: Date.now() })}
            color="#607D8B"
          />
          <View style={styles.space} />
          <Button
            title="Set attributes on manual span"
            onPress={() => manualSpan && manualSpan.setAttributes({ tries: 2, cached: false })}
            color="#795548"
          />
          <View style={styles.space} />
          <Button
            title="Record exception on manual span"
            onPress={() => manualSpan && manualSpan.recordException(new Error('ManualError'))}
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

