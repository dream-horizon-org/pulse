import { useState } from 'react';
import { View, Text, StyleSheet, Button, Alert } from 'react-native';
import { Pulse } from '@dreamhorizonorg/pulse-react-native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { NavigationContainer, useFocusEffect } from '@react-navigation/native';

const Stack = createNativeStackNavigator();
const navigationPendingRef = { value: false, source: '' };

function HomeScreen({ navigation }: any) {
  const [isNavigating, setIsNavigating] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const triggerNavigation = () => {
    if (isNavigating) return;

    setIsNavigating(true);
    navigationPendingRef.value = true;
    navigationPendingRef.source = 'HomeScreen';
    Pulse.trackEvent('navigation_start', {
      screen: 'Detail',
      source: 'HomeScreen',
    });
    navigation.navigate('Detail', { source: 'HomeScreen' });
  };

  useFocusEffect(() => {
    setIsNavigating(false);
  });

  const triggerNetworkCall = async () => {
    if (isLoading) return;

    setIsLoading(true);
    Pulse.trackEvent('network_call_start', {
      url: 'https://jsonplaceholder.typicode.com/posts/1',
    });

    try {
      const response = await fetch(
        'https://jsonplaceholder.typicode.com/posts/1'
      );
      await response.json();
      Pulse.trackEvent('network_call_end', {
        url: 'https://jsonplaceholder.typicode.com/posts/1',
        status: response.status,
      });
      Alert.alert('Success', 'Network call completed');
    } catch {
      Pulse.trackEvent('network_call_end', {
        url: 'https://jsonplaceholder.typicode.com/posts/1',
        error: 'true',
      });
      Alert.alert('Error', 'Network call failed');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>🎯 Interaction Demo</Text>
      <Text style={styles.subtitle}>Test interaction events</Text>

      <View style={styles.buttonContainer}>
        <Button
          title={isNavigating ? 'Navigating...' : 'Navigate to Detail'}
          onPress={triggerNavigation}
          disabled={isNavigating}
          color="#2196F3"
        />
      </View>

      <View style={styles.buttonContainer}>
        <Button
          title="Go to Source 2"
          onPress={() => navigation.navigate('Source2')}
          color="#FF9800"
        />
      </View>

      <View style={styles.buttonContainer}>
        <Button
          title={isLoading ? 'Loading...' : 'Trigger Network Call'}
          onPress={triggerNetworkCall}
          disabled={isLoading}
          color="#4CAF50"
        />
      </View>

      <View style={styles.infoContainer}>
        <Text style={styles.infoTitle}>Events:</Text>
        <Text style={styles.infoText}>
          • navigation_start (with source) → navigation_end (with source)
        </Text>
        <Text style={styles.infoText}>
          • network_call_start → network_call_end
        </Text>
        <Text style={styles.infoText}>
          Test: Navigate from HomeScreen and Source2Screen to Detail
        </Text>
        <Text style={styles.infoText}>
          Check logs for source property in events
        </Text>
      </View>
    </View>
  );
}

function Source2Screen({ navigation }: any) {
  const [isNavigating, setIsNavigating] = useState(false);

  const triggerNavigation = () => {
    if (isNavigating) return;

    setIsNavigating(true);
    navigationPendingRef.value = true;
    navigationPendingRef.source = 'Source2Screen';
    Pulse.trackEvent('navigation_start', {
      screen: 'Detail',
      source: 'Source2Screen',
    });
    navigation.navigate('Detail', { source: 'Source2Screen' });
  };

  useFocusEffect(() => {
    setIsNavigating(false);
  });

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Source 2 Screen</Text>
      <Text style={styles.subtitle}>Alternative navigation source</Text>

      <View style={styles.buttonContainer}>
        <Button
          title={isNavigating ? 'Navigating...' : 'Navigate to Detail'}
          onPress={triggerNavigation}
          disabled={isNavigating}
          color="#FF9800"
        />
      </View>

      <View style={styles.buttonContainer}>
        <Button
          title="← Back to Home"
          onPress={() => navigation.navigate('Home')}
          color="#666"
        />
      </View>
    </View>
  );
}

function DetailScreen({ navigation, route }: any) {
  useFocusEffect(() => {
    if (navigationPendingRef.value) {
      const source =
        route.params?.source || navigationPendingRef.source || 'unknown';
      Pulse.trackEvent('navigation_end', {
        screen: 'Detail',
        source: source,
      });
      navigationPendingRef.value = false;
      navigationPendingRef.source = '';
    }
  });

  const source = route.params?.source || 'unknown';

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Detail Screen</Text>
      <Text style={styles.subtitle}>Navigated from: {source}</Text>
      <View style={styles.buttonContainer}>
        <Button title="← Back" onPress={() => navigation.goBack()} />
      </View>
    </View>
  );
}

export default function InteractionDemo() {
  return (
    <NavigationContainer>
      <Stack.Navigator>
        <Stack.Screen
          name="Home"
          component={HomeScreen}
          options={{ title: 'Interaction Demo' }}
        />
        <Stack.Screen
          name="Source2"
          component={Source2Screen}
          options={{ title: 'Source 2' }}
        />
        <Stack.Screen
          name="Detail"
          component={DetailScreen}
          options={{ title: 'Detail' }}
        />
      </Stack.Navigator>
    </NavigationContainer>
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
    marginBottom: 8,
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
  infoContainer: {
    marginTop: 30,
    padding: 16,
    backgroundColor: '#f5f5f5',
    borderRadius: 8,
    width: '100%',
    maxWidth: 300,
  },
  infoTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    marginBottom: 8,
    color: '#333',
  },
  infoText: {
    fontSize: 13,
    color: '#666',
    marginBottom: 4,
  },
});
