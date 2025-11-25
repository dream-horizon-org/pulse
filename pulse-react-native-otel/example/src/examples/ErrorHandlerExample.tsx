import { View, Text, Button, StyleSheet, ScrollView, Alert } from 'react-native';
import {Pulse} from '@d11/pulse-react-native-otel';

export default function ErrorHandlerDemo() {
  const triggerUnhandledError = () => {
    Alert.alert(
      'Warning',
      'This will crash the app. The error will be auto-captured.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Crash',
          style: 'destructive',
          onPress: () => {
            setTimeout(() => {
              throw new Error('Unhandled error (demo)');
            }, 500);
          },
        },
      ]
    );
  };

  const triggerHandledError = () => {
    try {
      throw new Error('Handled error (demo)');
    } catch (error) {
      Pulse.reportException(error as Error, false);
      console.log('[ErrorDemo] Handled error reported');
      Alert.alert('Success', 'Handled error reported');
    }
  };

  const triggerHandledErrorWithSpan = () => {
    const span = Pulse.startSpan('handled_error_with_span');
    
    try {
      throw new Error('Handled error with span (demo)');
    } catch (error) {
      Pulse.reportException(error as Error, false);
      console.log('[ErrorDemo] Handled error with span reported');
    }
    
    setTimeout(() => {
      span.end();
      console.log('[ErrorDemo] Span ended');
      Alert.alert('Success', 'Error reported and span ended');
    }, 100);
  };

  const triggerUnhandledErrorWithSpan = () => {
    Alert.alert(
      'Warning',
      'This will crash the app with an active span.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Crash',
          style: 'destructive',
          onPress: () => {
            const span = Pulse.startSpan('unhandled_error_with_span');
            console.log('[ErrorDemo] Span started, will crash in 500ms');
            
            setTimeout(() => {
              throw new Error('Unhandled error with span (demo)');
              // span.end() won't be reached due to crash
            }, 500);
          },
        },
      ]
    );
  };

  return (
    <View style={styles.fullContainer}>
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.title}>Error Tracking Demo</Text>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Unhandled Crash</Text>
          <Button
            title="Trigger Unhandled Error"
            onPress={triggerUnhandledError}
            color="#D32F2F"
          />
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Handled Error</Text>
          <Button
            title="Trigger Handled Error"
            onPress={triggerHandledError}
            color="#4CAF50"
          />
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Handled Error with Span</Text>
          <Text style={styles.description}>
            Error should be correlated with the span's trace ID and span ID
          </Text>
          <Button
            title="Trigger Handled Error + Span"
            onPress={triggerHandledErrorWithSpan}
            color="#2196F3"
          />
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Unhandled Crash with Span</Text>
          <Text style={styles.description}>
            Crash occurs while span is active - tests auto-capture correlation
          </Text>
          <Button
            title="Trigger Unhandled Error + Span"
            onPress={triggerUnhandledErrorWithSpan}
            color="#FF5722"
          />
        </View>

        <View style={styles.infoBox}>
          <Text style={styles.infoText}>
            💡 Check logs for span correlation with errors
          </Text>
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  fullContainer: {
    flex: 1,
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
    marginVertical: 10,
    padding: 16,
    backgroundColor: '#f5f5f5',
    borderRadius: 8,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 8,
    textAlign: 'center',
    color: '#666',
  },
  description: {
    fontSize: 12,
    color: '#888',
    textAlign: 'center',
    marginBottom: 12,
    fontStyle: 'italic',
    lineHeight: 16,
  },
  infoBox: {
    marginTop: 20,
    padding: 16,
    backgroundColor: '#E3F2FD',
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: '#2196F3',
  },
  infoText: {
    fontSize: 12,
    color: '#1976D2',
    textAlign: 'center',
  },
});