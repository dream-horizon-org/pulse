import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../examples/NavigationExample';
import {
  Text,
  View,
  Button,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
} from 'react-native';
import { useEffect, useState } from 'react';
import { Pulse } from '@dreamhorizonorg/pulse-react-native';

export default function HomeScreen({
  navigation,
}: NativeStackScreenProps<RootStackParamList, 'Home'>) {
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadData = async () => {
      await new Promise((resolve) => setTimeout(resolve, 1500));
      setLoading(false);
      Pulse.markContentReady();
    };
    loadData();
  }, []);

  if (loading) {
    return <ActivityIndicator size="large" color="#2196f3" />;
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title}>🚀 Navigation Performance Tracking</Text>
      <Text style={styles.subtitle}>
        Navigate between screens to see automatic performance tracking in logcat
      </Text>

      <View style={styles.buttonContainer}>
        <Button
          title="Go to Profile"
          onPress={() => navigation.navigate('Profile', { userId: 'user-123' })}
        />
      </View>

      <View style={styles.buttonContainer}>
        <Button
          title="Go to Settings"
          onPress={() => navigation.navigate('Settings')}
          color="#ff9800"
        />
      </View>

      <View style={styles.buttonContainer}>
        <Button
          title="Go to Details"
          onPress={() => navigation.navigate('Details', { itemId: 42 })}
          color="#9c27b0"
        />
      </View>

      <Text style={styles.info}>
        💡 Tip: Check Android Logcat for navigation performance metrics
      </Text>
      <Text style={styles.infoDetail}>
        Each navigation will log: screen name, duration, source/destination
        routes
      </Text>
    </ScrollView>
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
    paddingHorizontal: 20,
  },
  content: {
    fontSize: 16,
    marginBottom: 12,
    textAlign: 'center',
    color: '#444',
  },
  buttonContainer: {
    marginVertical: 8,
    width: '100%',
    maxWidth: 250,
  },
  info: {
    marginTop: 30,
    fontSize: 13,
    color: '#2196f3',
    textAlign: 'center',
    fontWeight: '600',
  },
  infoDetail: {
    marginTop: 8,
    fontSize: 11,
    color: '#999',
    textAlign: 'center',
    paddingHorizontal: 30,
  },
});
