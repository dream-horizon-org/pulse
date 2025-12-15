import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Pressable,
  Alert,
} from 'react-native';
import {
  Pulse,
  type PulseAttributes,
  SpanStatusCode,
} from '@dreamhorizonorg/pulse-react-native';

export default function PulseAttributesExample() {

  // Comprehensive attributes with all types
  const getAllTypesAttributes = (): PulseAttributes => ({
    attr_string: 'test_string_value',
    attr_number: 42,
    attr_boolean: true,
    attr_string_array: ['value1', 'value2', 'value3'],
    attr_number_array: [1, 2, 3, 42],
    attr_boolean_array: [true, false, true],
  });

  // Test trackEvent
  const testTrackEvent = () => {
    Pulse.trackEvent('test_event_all_types', getAllTypesAttributes());
    Alert.alert('Success', 'trackEvent called with all attribute types');
  };

  // Test reportException
  const testReportException = () => {
    const testError = new Error('Test exception with all attribute types');
    Pulse.reportException(testError, false, getAllTypesAttributes());
    Alert.alert('Success', 'reportException called with all attribute types');
  };

  // Test trackSpan
  const testTrackSpan = () => {
    Pulse.trackSpan(
      'test_span_all_types',
      {
        attributes: getAllTypesAttributes(),
      },
      () => {
        return 'completed';
      }
    );
    Alert.alert('Success', 'trackSpan called with all attribute types');
  };

  // Test startSpan with all methods
  const testStartSpan = () => {
    const span = Pulse.startSpan('test_start_span_all_types', {
      attributes: getAllTypesAttributes(),
    });

    // Test setAttributes
    span.setAttributes({
      attr_string: 'updated_string',
      attr_number: 100,
      attr_boolean: false,
      attr_string_array: ['updated1', 'updated2'],
      attr_number_array: [100, 200],
      attr_boolean_array: [false, true],
    });

    // Test addEvent
    span.addEvent('test_event_in_span', {
      event_attr_string: 'event_string',
      event_attr_number: 200,
      event_attr_boolean: true,
      event_attr_string_array: ['a', 'b'],
      event_attr_number_array: [10, 20],
      event_attr_boolean_array: [false, true],
    });

    // Test recordException
    const testError = new Error('Test exception in span');
    span.recordException(testError, {
      exception_attr_string: 'exception_string',
      exception_attr_number: 300,
      exception_attr_boolean: false,
      exception_attr_string_array: ['x', 'y'],
      exception_attr_number_array: [30, 40],
      exception_attr_boolean_array: [true, false],
    });

    span.end(SpanStatusCode.OK);
    Alert.alert('Success', 'startSpan tested with all methods');
  };

  // Test setGlobalAttribute with all types
  const testSetGlobalAttribute = () => {
    Pulse.setGlobalAttribute('global_string', 'global_string_value');
    Pulse.setGlobalAttribute('global_number', 999);
    Pulse.setGlobalAttribute('global_boolean', true);
    Pulse.setGlobalAttribute('global_string_array', ['g1', 'g2']);
    Pulse.setGlobalAttribute('global_number_array', [99, 88]);
    Pulse.setGlobalAttribute('global_boolean_array', [true, false]);
    Alert.alert('Success', 'setGlobalAttribute called with all types');
  };

  return (
    <View style={styles.fullContainer}>
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.title}>PulseAttributes Testing</Text>
        <Text style={styles.subtitle}>
            Testing PulseAttributes with all attribute types (string, number, boolean,
          arrays)
        </Text>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Individual API Tests</Text>

          <Pressable
            style={({ pressed }) => [
              styles.button,
              styles.primaryButton,
              { opacity: pressed ? 0.6 : 1.0 },
            ]}
            onPress={testTrackEvent}
          >
            <Text style={styles.buttonText}>1. trackEvent</Text>
          </Pressable>

          <Pressable
            style={({ pressed }) => [
              styles.button,
              styles.primaryButton,
              { opacity: pressed ? 0.6 : 1.0 },
            ]}
            onPress={testReportException}
          >
            <Text style={styles.buttonText}>2. reportException</Text>
          </Pressable>

          <Pressable
            style={({ pressed }) => [
              styles.button,
              styles.primaryButton,
              { opacity: pressed ? 0.6 : 1.0 },
            ]}
            onPress={testTrackSpan}
          >
            <Text style={styles.buttonText}>3. trackSpan</Text>
          </Pressable>

          <Pressable
            style={({ pressed }) => [
              styles.button,
              styles.primaryButton,
              { opacity: pressed ? 0.6 : 1.0 },
            ]}
            onPress={testStartSpan}
          >
            <Text style={styles.buttonText}>
              4. startSpan
            </Text>
          </Pressable>

          <Pressable
            style={({ pressed }) => [
              styles.button,
              styles.primaryButton,
              { opacity: pressed ? 0.6 : 1.0 },
            ]}
            onPress={testSetGlobalAttribute}
          >
            <Text style={styles.buttonText}>5. setGlobalAttribute</Text>
          </Pressable>
        </View>

        <Text style={styles.info}>
          💡 Each API is tested with all attribute types: string, number,
          boolean, string[], number[], boolean[]
        </Text>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  fullContainer: {
    flex: 1,
    backgroundColor: '#fff',
  },
  container: {
    padding: 20,
    paddingBottom: 40,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 8,
    textAlign: 'center',
    color: '#333',
  },
  subtitle: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    marginBottom: 24,
    fontStyle: 'italic',
  },
  section: {
    marginBottom: 24,
    padding: 16,
    backgroundColor: '#f9f9f9',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#e0e0e0',
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 12,
    color: '#2196F3',
  },
  button: {
    padding: 14,
    borderRadius: 6,
    marginBottom: 12,
    alignItems: 'center',
  },
  primaryButton: {
    backgroundColor: '#2196F3',
  },
  successButton: {
    backgroundColor: '#4CAF50',
  },
  buttonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  info: {
    marginTop: 16,
    padding: 12,
    backgroundColor: '#E3F2FD',
    borderRadius: 6,
    fontSize: 13,
    color: '#1976D2',
    textAlign: 'center',
  },
});

