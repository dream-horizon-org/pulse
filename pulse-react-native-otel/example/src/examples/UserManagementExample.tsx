import { useState } from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import {
  Pulse,
  PulseDataCollectionConsent,
  type PulseAttributes,
} from '@dreamhorizonorg/pulse-react-native';

function generateUUID(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export default function UserManagementExample() {
  const [userId, setUserId] = useState<string | null>(null);

  const handleSetUserAndEvent = () => {
    const uuid = generateUUID();
    const properties: PulseAttributes = {
      email: `user_${Date.now()}@example.com`,
      name: 'Test User',
      plan: 'premium',
    };

    Pulse.setUserId(uuid);
    Pulse.setUserProperties(properties);
    Pulse.trackEvent('user_set', { action: 'user_identified' });

    setUserId(uuid);
  };

  const handleRemoveUserAndEvent = () => {
    Pulse.setUserId(null);
    Pulse.setUserProperties({});
    Pulse.trackEvent('user_removed', { action: 'user_cleared' });

    setUserId(null);
  };

  const handleSetDataCollectionStateToDenied = () => {
    Pulse.setDataCollectionState(PulseDataCollectionConsent.DENIED);
  };

  const handleSetDataCollectionStateToAllowed = () => {
    Pulse.setDataCollectionState(PulseDataCollectionConsent.ALLOWED);
  };

  const handleSetDataCollectionStateToPending = () => {
    Pulse.setDataCollectionState(PulseDataCollectionConsent.PENDING);
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>User Management</Text>
      <Text style={styles.status}>User ID: {userId || '(not set)'}</Text>

      <Pressable
        style={({ pressed }) => [
          styles.button,
          { opacity: pressed ? 0.6 : 1.0 },
        ]}
        onPress={handleSetUserAndEvent}
      >
        <Text style={styles.buttonText}>Set User & Trigger Event</Text>
      </Pressable>

      <Pressable
        style={({ pressed }) => [
          styles.button,
          styles.removeButton,
          { opacity: pressed ? 0.6 : 1.0 },
        ]}
        onPress={handleRemoveUserAndEvent}
      >
        <Text style={styles.buttonText}>Remove User & Trigger Event</Text>
      </Pressable>

      <Pressable
        style={({ pressed }) => [
          styles.button,
          styles.removeButton,
          { opacity: pressed ? 0.6 : 1.0 },
        ]}
        onPress={handleSetDataCollectionStateToDenied}
      >
        <Text style={styles.buttonText}>
          Set Data Collection State to Denied
        </Text>
      </Pressable>
      <Pressable
        style={({ pressed }) => [
          styles.button,
          styles.removeButton,
          { opacity: pressed ? 0.6 : 1.0 },
        ]}
        onPress={handleSetDataCollectionStateToAllowed}
      >
        <Text style={styles.buttonText}>
          Set Data Collection State to Allowed
        </Text>
      </Pressable>
      <Pressable
        style={({ pressed }) => [
          styles.button,
          styles.removeButton,
          { opacity: pressed ? 0.6 : 1.0 },
        ]}
        onPress={handleSetDataCollectionStateToPending}
      >
        <Text style={styles.buttonText}>
          Set Data Collection State to Pending
        </Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    backgroundColor: '#fff',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 16,
    textAlign: 'center',
  },
  status: {
    fontSize: 14,
    marginBottom: 24,
    textAlign: 'center',
    color: '#666',
  },
  button: {
    padding: 16,
    borderRadius: 8,
    marginBottom: 12,
    backgroundColor: '#2196F3',
    alignItems: 'center',
  },
  removeButton: {
    backgroundColor: '#f44336',
  },
  buttonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
});
