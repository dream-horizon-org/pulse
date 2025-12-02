import { useState } from 'react';
import { View, Text, StyleSheet, Button, Alert } from 'react-native';
import PulseReactNativeOtel from '../../../src/NativePulseReactNativeOtel';
import FrozenFrameStressTest from './FrozenFrameStressTest';

export default function AndroidFeaturesExample() {
  const [showStressTest, setShowStressTest] = useState(false);

  const triggerANR = () => {
    Alert.alert(
      'ANR Test',
      'This will freeze the main thread for 6 seconds. The app may become unresponsive.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Trigger ANR',
          style: 'destructive',
          onPress: () => {
            try {
              PulseReactNativeOtel.triggerAnr();
            } catch (error) {
              Alert.alert('Error', `ANR trigger failed: ${error}`);
            }
          },
        },
      ]
    );
  };

  if (showStressTest) {
    return (
      <View style={styles.fullScreen}>
        <View style={styles.backButton}>
          <Button
            title="← Back"
            onPress={() => setShowStressTest(false)}
            color="#666"
          />
        </View>
        <FrozenFrameStressTest />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>🤖 Android Features</Text>
      <Text style={styles.subtitle}>Test Android-specific features</Text>

      <View style={styles.buttonContainer}>
        <Button title="Trigger ANR" onPress={triggerANR} color="#F44336" />
      </View>

      <View style={styles.buttonContainer}>
        <Button
          title="Frozen Frame Stress Test"
          onPress={() => setShowStressTest(true)}
          color="#FF9800"
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 20,
    backgroundColor: '#fff',
  },
  fullScreen: {
    flex: 1,
  },
  backButton: {
    padding: 8,
    backgroundColor: '#f5f5f5',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 16,
    textAlign: 'center',
    color: '#333',
  },
  subtitle: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    marginBottom: 30,
  },
  buttonContainer: {
    marginVertical: 8,
    width: '100%',
    maxWidth: 250,
  },
});
