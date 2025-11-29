import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../examples/NavigationExample';
import {
  Text,
  View,
  Button,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import React from 'react';

export default function ProfileScreen({
  route,
  navigation,
}: NativeStackScreenProps<RootStackParamList, 'Profile'>) {
  const { userId } = route.params;
  const [loading, setLoading] = React.useState(true);

  // Simulate loading data
  React.useEffect(() => {
    const timer = setTimeout(() => setLoading(false), 500);
    return () => clearTimeout(timer);
  }, []);

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Profile Screen</Text>
      <Text style={styles.content}>User ID: {userId}</Text>

      {loading ? (
        <ActivityIndicator size="large" color="#2196f3" />
      ) : (
        <>
          <Text style={styles.content}>Profile loaded successfully!</Text>
          <Text style={styles.info}>
            ✅ Navigation performance was tracked automatically
          </Text>
        </>
      )}

      <View style={styles.buttonContainer}>
        <Button
          title="Go to Details"
          onPress={() => navigation.navigate('Details', { itemId: 99 })}
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
          title="Back to Home"
          onPress={() => navigation.navigate('Home')}
          color="#4caf50"
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
