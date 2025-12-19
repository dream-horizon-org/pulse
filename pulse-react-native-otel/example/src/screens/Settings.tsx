import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../examples/NavigationExample';
import { Text, View, Button, StyleSheet } from 'react-native';
import React from 'react';
import { Pulse } from '@dreamhorizonorg/pulse-react-native';

export default function SettingsScreen({
  navigation,
}: NativeStackScreenProps<RootStackParamList, 'Settings'>) {
  React.useEffect(() => {
    Pulse.markContentReady();
  }, []);

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Settings Screen</Text>
      <Text style={styles.content}>Configure your app settings here</Text>

      <View style={styles.buttonContainer}>
        <Button
          title="Go to Profile"
          onPress={() =>
            navigation.navigate('Profile', { userId: 'settings-user' })
          }
        />
      </View>

      <View style={styles.buttonContainer}>
        <Button
          title="Back"
          onPress={() => navigation.goBack()}
          color="#f44336"
        />
      </View>

      <Text style={styles.info}>🔙 Back navigation is also tracked!</Text>
      <Text style={styles.infoDetail}>
        ✅ Screen interactive marked ready immediately (no async loading)
      </Text>
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
