import { StatusBar } from 'expo-status-bar';
import { StyleSheet, Text, View, Button, Alert } from 'react-native';
import { Pulse } from '@dreamhorizonorg/pulse-react-native';
import { useEffect } from 'react';

Pulse.start();

export default function App() {
  useEffect(() => {
    // Set user properties
    Pulse.setUserProperties({
      userId: 'expo-example-user',
      environment: 'development',
    });

    // Track an event
    Pulse.trackEvent('test_app_started', {
      platform: 'expo',
    });
  }, []);

  const handleButtonPress = () => {
    Pulse.trackEvent('button_pressed', {
      buttonName: 'test_button',
    });
    Alert.alert('Success', 'Event tracked!');
  };

  const handleError = () => {
    try {
      throw new Error('Test error from Expo app');
    } catch (error) {
      Pulse.trackEvent(error.message, {
        source: 'expo-example',
      });
      Alert.alert('Error Tracked', 'Check your backend for the error');
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Pulse Expo Example</Text>

      <View style={styles.buttonContainer}>
        <Button title="Track Event" onPress={handleButtonPress} />
      </View>

      <View style={styles.buttonContainer}>
        <Button title="Track Error" onPress={handleError} color="red" />
      </View>

      <StatusBar style="auto" />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 20,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 10,
  },
  subtitle: {
    fontSize: 14,
    textAlign: 'center',
    marginBottom: 30,
    color: '#666',
  },
  buttonContainer: {
    marginVertical: 10,
    width: '100%',
    maxWidth: 300,
  },
  info: {
    marginTop: 30,
    fontSize: 12,
    textAlign: 'center',
    color: '#999',
    fontStyle: 'italic',
  },
});
