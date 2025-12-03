import React from 'react';
import { Text, View, StyleSheet, ScrollView, Pressable } from 'react-native';
import { Pulse } from '@dreamhorizonorg/pulse-react-native';
import ButtonWithTitle from './components/ButtonWithTitle';
import NavigationExample from './examples/NavigationExample';
import TraceExample from './examples/TraceExample';
import StackExample from './examples/StackExample';
import EventExample from './examples/EventExample';
import ErrorHandlerExample from './examples/ErrorHandlerExample';
import NetworkInterceptorExample from './examples/NetworkInterceptorExample';
import ErrorBoundaryExample from './examples/ErrorBoundaryExample';
import AndroidFeaturesExample from './examples/AndroidFeaturesExample';
import InteractionDemo from './examples/InteractionDemo';

Pulse.start();

type DemoConfig = {
  id: string;
  label: string;
  title: string;
  color: string;
  component: React.ComponentType;
};

const DEMO_CONFIGS: DemoConfig[] = [
  {
    id: 'navigation',
    label: '🚀 Native Stack Navigation',
    title: 'Native Stack Navigation Tracking',
    color: '#2196F3',
    component: NavigationExample,
  },
  {
    id: 'stack',
    label: '📱 JS Stack Navigator',
    title: 'JS Stack Navigation Tracking',
    color: '#4CAF50',
    component: StackExample,
  },
  {
    id: 'errorHandler',
    label: '🛡️ Error Handling',
    title: 'Global Error Handler & Crash Reports',
    color: '#F44336',
    component: ErrorHandlerExample,
  },
  {
    id: 'network',
    label: '🌐 Network Monitoring',
    title: 'HTTP Request Interceptor',
    color: '#9c27b0',
    component: NetworkInterceptorExample,
  },
  {
    id: 'trace',
    label: '📊 Tracing & Spans',
    title: 'Tracing & Spans Tracking',
    color: '#FF9800',
    component: TraceExample,
  },
  {
    id: 'event',
    label: '📝 Event Tracking',
    title: 'Custom Events & Analytics',
    color: '#00BCD4',
    component: EventExample,
  },
  {
    id: 'errorBoundary',
    label: '🛡️ Error Boundary',
    title: 'Error Boundary Demo',
    color: '#FF9800',
    component: ErrorBoundaryExample,
  },
  {
    id: 'androidFeatures',
    label: '🤖 Android Features',
    title: 'Android Features Testing',
    color: '#795548',
    component: AndroidFeaturesExample,
  },
  {
    id: 'interaction',
    label: '🎯 Interaction Demo',
    title: 'Interaction Event Tracking',
    color: '#9C27B0',
    component: InteractionDemo,
  },
];

type DemoScreenProps = {
  demo: DemoConfig;
  onBack: () => void;
};

function DemoScreen({ demo, onBack }: DemoScreenProps) {
  const DemoComponent = demo.component;

  return (
    <View style={styles.fullContainer}>
      <Pressable
        style={({ pressed }) => [
          styles.backButton,
          { opacity: pressed ? 0.6 : 1.0 },
        ]}
        onPress={onBack}
      >
        <Text style={styles.backButtonText}>← Back to Tests</Text>
      </Pressable>
      <DemoComponent />
    </View>
  );
}

export default function App() {
  const [activeDemo, setActiveDemo] = React.useState<string | null>(null);

  const activeDemoConfig = DEMO_CONFIGS.find((demo) => demo.id === activeDemo);

  if (activeDemoConfig) {
    return (
      <DemoScreen demo={activeDemoConfig} onBack={() => setActiveDemo(null)} />
    );
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title}>Pulse React Native OpenTelemetry Test</Text>

      {DEMO_CONFIGS.map((demo) => (
        <ButtonWithTitle
          key={demo.id}
          label={demo.label}
          title={demo.title}
          color={demo.color}
          onPress={() => setActiveDemo(demo.id)}
        />
      ))}

      <Text style={styles.info}>
        Check Android Logcat for OpenTelemetry telemetry data
      </Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  fullContainer: {
    flex: 1,
  },
  backButton: {
    padding: 15,
    backgroundColor: '#f0f0f0',
    borderBottomWidth: 1,
    borderBottomColor: '#ddd',
    marginTop: 12,
  },
  backButtonText: {
    fontSize: 16,
    color: '#2196F3',
    fontWeight: '600',
  },
  container: {
    flexGrow: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 20,
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 30,
    textAlign: 'center',
  },

  info: {
    marginTop: 30,
    fontSize: 12,
    color: '#666',
    textAlign: 'center',
    fontStyle: 'italic',
  },
});
