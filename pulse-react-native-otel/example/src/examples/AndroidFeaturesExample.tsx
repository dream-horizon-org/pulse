import { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Button,
  Alert,
  Platform,
  ScrollView,
} from 'react-native';
import PulseReactNativeOtel from '../../../src/NativePulseReactNativeOtel';
import FrozenFrameStressTest from './FrozenFrameStressTest';
import NativePulseExampleModule from '../specs/NativePulseExampleModule';

export default function AndroidFeaturesExample() {
  const [showStressTest, setShowStressTest] = useState(false);
  const [loading, setLoading] = useState<string | null>(null);

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

  // Native Android OkHttp GET request
  const testNativeGet = async () => {
    if (Platform.OS !== 'android' || !NativePulseExampleModule) {
      Alert.alert(
        'Not Available',
        'Native network module is only available on Android'
      );
      return;
    }

    setLoading('native-get');
    try {
      console.log(
        '[Pulse Network] 🤖 [Native Android] Making OkHttp GET request...'
      );
      const result = await NativePulseExampleModule.makeGetRequest(
        'https://jsonplaceholder.typicode.com/posts/1'
      );
      const data = JSON.parse(result.body);
      Alert.alert(
        'Success',
        `Native GET: ${data.title} (Status: ${result.status})`
      );
    } catch (error: any) {
      Alert.alert('Error', `Native GET Error: ${error.message}`);
    } finally {
      setLoading(null);
    }
  };

  // Native Android OkHttp POST request
  const testNativePost = async () => {
    if (Platform.OS !== 'android' || !NativePulseExampleModule) {
      Alert.alert(
        'Not Available',
        'Native network module is only available on Android'
      );
      return;
    }

    setLoading('native-post');
    try {
      console.log(
        '[Pulse Network] 🤖 [Native Android] Making OkHttp POST request...'
      );
      const postBody = JSON.stringify({
        title: 'Test Post from Native Android',
        body: 'This is a test POST request from native OkHttp',
        userId: 1,
      });
      const result = await NativePulseExampleModule.makePostRequest(
        'https://jsonplaceholder.typicode.com/posts',
        postBody
      );
      const data = JSON.parse(result.body);
      Alert.alert(
        'Success',
        `Native POST: Created post #${data.id} (Status: ${result.status})`
      );
    } catch (error: any) {
      Alert.alert('Error', `Native POST Error: ${error.message}`);
    } finally {
      setLoading(null);
    }
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
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title}>🤖 Android Features</Text>
      <Text style={styles.subtitle}>Test Android-specific features</Text>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Performance Monitoring</Text>

        <View style={styles.buttonContainer}>
          <Button
            title="Trigger ANR"
            onPress={triggerANR}
            disabled={loading !== null}
            color="#F44336"
          />
        </View>

        <View style={styles.buttonContainer}>
          <Button
            title="Frozen Frame Stress Test"
            onPress={() => setShowStressTest(true)}
            disabled={loading !== null}
            color="#FF9800"
          />
        </View>
      </View>

      {Platform.OS === 'android' && NativePulseExampleModule && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Native Network (OkHttp)</Text>

          <View style={styles.buttonContainer}>
            <Button
              title={
                loading === 'native-get' ? 'Loading...' : 'Native OkHttp GET'
              }
              onPress={testNativeGet}
              disabled={loading !== null}
              color="#795548"
            />
          </View>

          <View style={styles.buttonContainer}>
            <Button
              title={
                loading === 'native-post' ? 'Loading...' : 'Native OkHttp POST'
              }
              onPress={testNativePost}
              disabled={loading !== null}
              color="#795548"
            />
          </View>
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
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
  section: {
    marginVertical: 12,
    padding: 16,
    backgroundColor: '#f5f5f5',
    borderRadius: 8,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 12,
    textAlign: 'center',
    color: '#333',
  },
  sectionDescription: {
    fontSize: 12,
    color: '#666',
    fontStyle: 'italic',
    marginBottom: 12,
    textAlign: 'center',
  },
  buttonContainer: {
    marginVertical: 8,
    width: '100%',
  },
});
