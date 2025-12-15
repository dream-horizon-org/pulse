import { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Pressable,
  Alert,
} from 'react-native';
import { Pulse, type PulseAttributes } from '@dreamhorizonorg/pulse-react-native';

// Simple UUID v4 generator
function generateUUID(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export default function UserManagementExample() {
  const [currentUserId, setCurrentUserId] = useState<string | null>(null);
  const [currentProperties, setCurrentProperties] = useState<PulseAttributes>({});

  // 1. Set User ID
  const handleSetUserId = () => {
    const uuid = generateUUID();
    Pulse.setUserId(uuid);
    setCurrentUserId(uuid);
    console.log('[UserManagement] Set userId:', uuid);
    Alert.alert('Success', `User ID set: ${uuid}`);
  };

  // 2. Set Single Property
  const handleSetSingleProperty = () => {
    const email = `user_${Date.now()}@example.com`;
    Pulse.setUserProperty('email', email);
    setCurrentProperties((prev) => ({ ...prev, email }));
    console.log('[UserManagement] Set single property (email):', email);
    Alert.alert('Success', `Single property set - email: ${email}`);
  };

  // 3. Set Multiple Properties
  const handleSetMultipleProperties = () => {
    const properties: PulseAttributes = {
      email: `user_${Date.now()}@example.com`,
      mobile: `+1-555-${Math.floor(Math.random() * 10000)
        .toString()
        .padStart(4, '0')}`,
      name: 'Test User',
      plan: 'premium',
    };
    Pulse.setUserProperties(properties);
    setCurrentProperties(properties);
    console.log('[UserManagement] Set multiple properties:', properties);
    Alert.alert('Success', 'Multiple properties set (email, mobile, name, plan)');
  };

  // 4. Trigger Event and Observe User Properties
  const handleTriggerEvent = () => {
    // Ensure user info is set first
    if (!currentUserId) {
      const uuid = generateUUID();
      Pulse.setUserId(uuid);
      setCurrentUserId(uuid);
    }

    if (Object.keys(currentProperties).length === 0) {
      const properties: PulseAttributes = {
        email: `user_${Date.now()}@example.com`,
        mobile: `+1-555-${Math.floor(Math.random() * 10000)
          .toString()
          .padStart(4, '0')}`,
      };
      Pulse.setUserProperties(properties);
      setCurrentProperties(properties);
    }

    // Track an event - this will include user info
    Pulse.trackEvent('user_action_test', {
      action: 'test_with_user_info',
      timestamp: Date.now().toString(),
    });

    console.log('[UserManagement] Event tracked with user info:', {
      userId: currentUserId,
      properties: currentProperties,
    });
    Alert.alert(
      'Success',
      'Event tracked! Check logs to verify user ID and properties are included in the event.'
    );
  };

  return (
    <View style={styles.fullContainer}>
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.title}>User Management Demo</Text>
        <Text style={styles.subtitle}>
          Set user properties and trigger events to observe them in telemetry
        </Text>

        {/* Current Status */}
        <View style={styles.statusSection}>
          <Text style={styles.statusTitle}>Current Status:</Text>
          <Text style={styles.statusText}>
            User ID: {currentUserId || '(not set)'}
          </Text>
          <Text style={styles.statusText}>
            Properties:{' '}
            {Object.keys(currentProperties).length > 0
              ? Object.entries(currentProperties)
                  .map(([k, v]) => `${k}: ${v}`)
                  .join(', ')
              : '(none)'}
          </Text>
        </View>

        {/* Actions */}
        <View style={styles.section}>
          <Pressable
            style={({ pressed }) => [
              styles.button,
              styles.primaryButton,
              { opacity: pressed ? 0.6 : 1.0 },
            ]}
            onPress={handleSetUserId}
          >
            <Text style={styles.buttonText}>1. Set User ID</Text>
          </Pressable>

          <Pressable
            style={({ pressed }) => [
              styles.button,
              styles.secondaryButton,
              { opacity: pressed ? 0.6 : 1.0 },
            ]}
            onPress={handleSetSingleProperty}
          >
            <Text style={styles.buttonText}>2. Set Single Property</Text>
          </Pressable>

          <Pressable
            style={({ pressed }) => [
              styles.button,
              styles.secondaryButton,
              { opacity: pressed ? 0.6 : 1.0 },
            ]}
            onPress={handleSetMultipleProperties}
          >
            <Text style={styles.buttonText}>3. Set Multiple Properties</Text>
          </Pressable>

          <Pressable
            style={({ pressed }) => [
              styles.button,
              styles.successButton,
              { opacity: pressed ? 0.6 : 1.0 },
            ]}
            onPress={handleTriggerEvent}
          >
            <Text style={styles.buttonText}>
              4. Trigger Event & Observe User Properties
            </Text>
          </Pressable>
        </View>

        <Text style={styles.info}>
          💡 After setting user ID and properties, trigger an event to see them
          included in the telemetry data.
        </Text>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  fullContainer: {
    flex: 1,
    backgroundColor: '#fff',
  },
  container: {
    padding: 20,
    paddingBottom: 40,
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
    marginBottom: 24,
    fontStyle: 'italic',
  },
  statusSection: {
    marginBottom: 24,
    padding: 16,
    backgroundColor: '#E3F2FD',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#BBDEFB',
  },
  statusTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 8,
    color: '#1976D2',
  },
  statusText: {
    fontSize: 14,
    color: '#333',
    marginBottom: 4,
  },
  section: {
    marginBottom: 24,
    padding: 16,
    backgroundColor: '#f9f9f9',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#e0e0e0',
  },
  button: {
    padding: 16,
    borderRadius: 6,
    marginBottom: 12,
    alignItems: 'center',
  },
  primaryButton: {
    backgroundColor: '#2196F3',
  },
  secondaryButton: {
    backgroundColor: '#757575',
  },
  successButton: {
    backgroundColor: '#4CAF50',
  },
  buttonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  info: {
    marginTop: 16,
    padding: 12,
    backgroundColor: '#E3F2FD',
    borderRadius: 6,
    fontSize: 13,
    color: '#1976D2',
    textAlign: 'center',
  },
});
