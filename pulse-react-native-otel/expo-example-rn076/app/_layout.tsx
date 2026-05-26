import { Pulse } from '@dreamhorizonorg/pulse-react-native';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';

// CI smoke target: RN 0.76 ships Kotlin 1.9.25 by default; this sample exercises
// `android.kotlin19Compat: true` end to end (plugin -> gradle.properties -> SDK cap).
Pulse.start({
  autoDetectExceptions: true,
  autoDetectNetwork: true,
});

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <Stack />
      <StatusBar style="auto" />
    </SafeAreaProvider>
  );
}
