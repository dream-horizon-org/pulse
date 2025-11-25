import { useState } from 'react';
import { View, Text, Button, StyleSheet, ScrollView } from 'react-native';
import {Pulse} from '@d11/pulse-react-native-otel';
import type { FallbackRender } from '@d11/pulse-react-native-otel';

const TAG = '[Pulse ErrorBoundaryDemo]';

const ThrowErrorComponent = ({ shouldThrow }: { shouldThrow: boolean }) => {
  if (shouldThrow) {
    throw new Error('This is a test error from ThrowErrorComponent of ErrorBoundaryExample!');
  }
  return <Text style={styles.successText}>✅ Component rendered successfully</Text>;
};

const NullErrorComponent = ({ shouldThrow }: { shouldThrow: boolean }) => {
  if (shouldThrow) {
    throw null;
  }
  return <Text style={styles.successText}>✅ Null error component rendered successfully</Text>;
};

const wrappedStyles = StyleSheet.create({
  wrappedText: {
    fontSize: 14,
    color: '#2196F3',
    marginBottom: 10,
  },
  errorText: {
    fontSize: 14,
    color: '#FF6B6B',
    marginBottom: 10,
    fontWeight: '600',
  },
});

const WrappedComponent = Pulse.withErrorBoundary(
  ({ name, shouldThrow }: { name: string, shouldThrow: boolean }) => {
    if (shouldThrow) {
      throw new Error('Error in wrapped component!');
    }
      return <Text>Hello, {name}! 👋</Text>;
  },
  {
    fallback: <Text style={wrappedStyles.errorText}>⚠️ Wrapped component failed</Text>,
    onError: () => {
      console.log(TAG, 'Wrapped component error');
    },
  }
);

const fallbackStyles = StyleSheet.create({
  fallbackContainer: {
    padding: 15,
    backgroundColor: '#FFF3E0',
    borderRadius: 8,
    borderWidth: 2,
    borderColor: '#FF9800',
  },
  fallbackTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#E65100',
    marginBottom: 10,
  },
  fallbackError: {
    fontSize: 14,
    color: '#D84315',
    marginBottom: 10,
  },
  fallbackStack: {
    fontSize: 10,
    color: '#666',
    marginBottom: 10,
    fontFamily: 'monospace',
  },
});

const ErrorFallback: FallbackRender = ({ error, componentStack}) => (
  <View style={fallbackStyles.fallbackContainer}>
    <Text style={fallbackStyles.fallbackTitle}>❌ Something went wrong</Text>
    <Text style={fallbackStyles.fallbackError}>
      {error instanceof Error ? error.message : String(error)}
    </Text>
    <Text style={fallbackStyles.fallbackStack} numberOfLines={5}>
      {componentStack}
    </Text>
  </View>
);

export default function ErrorBoundaryDemo() {
    const [throwError0, setThrowError0] = useState(false);
  const [throwError1, setThrowError1] = useState(false);
  const [throwError2, setThrowError2] = useState(false);
  const [shouldThrowWrappedComponent, setShouldThrowWrappedComponent] = useState(false);

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.title}>Error Boundary Demo</Text>

        <View style={styles.section}>
            <Text style={styles.sectionTitle}>0. Basic Error Boundary no fallback</Text>
            <Pulse.ErrorBoundary>
                <ThrowErrorComponent shouldThrow={throwError0} />
            </Pulse.ErrorBoundary>
            <Button
            title={throwError0 ? 'Error Thrown' : 'Throw Error'}
            onPress={() => setThrowError0(true)}
            disabled={throwError0}
            color="#4CAF50"
            />
        </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>1. Error Boundary with Custom Fallback</Text>
        <Pulse.ErrorBoundary fallback={ErrorFallback}>
          <ThrowErrorComponent shouldThrow={throwError1} />
        </Pulse.ErrorBoundary>
        <Button
          title={throwError1 ? 'Error Thrown' : 'Throw Error with Fallback'}
          onPress={() => setThrowError1(true)}
          disabled={throwError1}
          color="#4CAF50"
        />
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>2. Null Error Handling</Text>
        <Pulse.ErrorBoundary fallback={<Text style={styles.errorText}>⚠️ Null error caught</Text>}>
          <NullErrorComponent shouldThrow={throwError2} />
        </Pulse.ErrorBoundary>
        <Button
          title={throwError2 ? 'Null Thrown' : 'Throw Null Error'}
          onPress={() => setThrowError2(true)}
          disabled={throwError2}
          color="#9C27B0"
        />
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>3. HOC withErrorBoundary</Text>
        <WrappedComponent name="Test User" shouldThrow={shouldThrowWrappedComponent} />
        <Button
          title={shouldThrowWrappedComponent ? 'Error Thrown' : 'Throw Error with HOC'}
          onPress={() => setShouldThrowWrappedComponent(true)}
          disabled={shouldThrowWrappedComponent}
          color="#2196F3"
        />
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    marginBottom: 30,
    backgroundColor: '#f5f5f5',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 20,
    textAlign: 'center',
  },
  section: {
    marginVertical: 15,
    padding: 15,
    backgroundColor: 'white',
    borderRadius: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 10,
    color: '#333',
  },
  successText: {
    fontSize: 14,
    color: '#4CAF50',
    marginBottom: 10,
  },
  errorText: {
    fontSize: 14,
    color: '#FF6B6B',
    marginBottom: 10,
    fontWeight: '600',
  },
});
