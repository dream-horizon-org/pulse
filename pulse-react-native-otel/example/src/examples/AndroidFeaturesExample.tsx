import { View, Text, StyleSheet, Button, Alert } from 'react-native';
import PulseReactNativeOtel from '../../../src/NativePulseReactNativeOtel';

export default function AndroidFeaturesExample() {
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

  return (
    <View style={styles.container}>
      <Text style={styles.title}>🤖 Android Features</Text>
      <Text style={styles.subtitle}>Test Android-specific features</Text>

      <View style={styles.buttonContainer}>
        <Button
          title="Trigger ANR"
          onPress={triggerANR}
          color="#F44336"
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

