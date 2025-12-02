import { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  Button,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  Alert,
} from 'react-native';
import { Pulse, type PulseAttributes, type PulseAttributeValue } from '@dreamhorizonorg/pulse-react-native';

export default function EventDemo() {
  const [eventName, setEventName] = useState('');
  const [attributeKey, setAttributeKey] = useState('');
  const [attributeValue, setAttributeValue] = useState<PulseAttributeValue>('');
  const [attributes, setAttributes] = useState<PulseAttributes>({});

  const addAttribute = () => {
    if (!attributeKey.trim()) {
      Alert.alert('Error', 'Attribute key cannot be empty');
      return;
    }
    setAttributes((prev) => ({
      ...prev,
      [attributeKey.trim()]: attributeValue,
    }));
    setAttributeKey('');
    setAttributeValue('');
  };

  const removeAttribute = (key: string) => {
    setAttributes((prev) => {
      const updated = { ...prev };
      delete updated[key];
      return updated;
    });
  };

  const trackCustomEvent = () => {
    if (!eventName.trim()) {
      Alert.alert('Error', 'Event name cannot be empty');
      return;
    }

    const finalAttributes = Object.keys(attributes).length > 0 ? attributes : undefined;
    Pulse.trackEvent(eventName.trim(), finalAttributes);
    
    console.log('[EventDemo] Tracked custom event:', {
      event: eventName.trim(),
      attributes: finalAttributes,
    });

    Alert.alert('Success', `Event "${eventName}" tracked!`);
  };

  const clearForm = () => {
    setEventName('');
    setAttributes({});
    setAttributeKey('');
    setAttributeValue('');
  };

  // Default test event
  const trackDefaultEvent = () => {
    Pulse.trackEvent('user_signup', {
      plan: 'pro',
      source: 'mobile',
      timestamp: Date.now().toString(),
    });
    console.log('[EventDemo] Tracked default event: user_signup');
    Alert.alert('Success', 'Tracked: user_signup');
  };

  // Event with active span (for correlation testing)
  const trackEventWithSpan = () => {
    const span = Pulse.startSpan('event_with_span', {
      attributes: { test: 'event_correlation' }
    });
    
    Pulse.trackEvent('test_event_with_span', {
      userId: '12345',
      action: 'test',
    });
    
    console.log('[EventDemo] Event tracked with active span');
    
    setTimeout(() => {
      span.end();
      console.log('[EventDemo] Span ended');
      Alert.alert('Success', 'Event tracked with span - check logs for correlation');
    }, 100);
  };

  return (
    <View style={styles.fullContainer}>
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.title}>Event Tracking Demo</Text>

        {/* Custom Event Section */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Custom Event</Text>
          
          <TextInput
            style={styles.input}
            placeholder="Event name (e.g., user_login)"
            value={eventName}
            onChangeText={setEventName}
          />

          <View style={styles.attributeInputRow}>
            <TextInput
              style={[styles.input, styles.attributeKeyInput]}
              placeholder="Attribute key"
              value={attributeKey}
              onChangeText={setAttributeKey}
            />
            <TextInput
              style={[styles.input, styles.attributeValueInput]}
              placeholder="Attribute value"
              value={attributeValue.toString()}
              onChangeText={setAttributeValue}
            />
            <TouchableOpacity style={styles.addButton} onPress={addAttribute}>
              <Text style={styles.addButtonText}>+</Text>
            </TouchableOpacity>
          </View>

          {Object.keys(attributes).length > 0 && (
            <View style={styles.attributesList}>
              <Text style={styles.attributesTitle}>Attributes:</Text>
              {Object.entries(attributes).map(([key, value]) => (
                <View key={key} style={styles.attributeItem}>
                  <Text style={styles.attributeText}>
                    {key}: {value}
                  </Text>
                  <TouchableOpacity onPress={() => removeAttribute(key)}>
                    <Text style={styles.removeButton}>✕</Text>
                  </TouchableOpacity>
                </View>
              ))}
            </View>
          )}

          <View style={styles.buttonRow}>
            <Button title="Track Event" onPress={trackCustomEvent} />
            <View style={styles.space} />
            <Button title="Clear" onPress={clearForm} color="#FF5722" />
          </View>
        </View>

        {/* Predefined Events Section */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Quick Test Events</Text>
          
          <Button
            title="Track: user_signup"
            onPress={trackDefaultEvent}
            color="#4CAF50"
          />
        </View>

        {/* Event with Span Section */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Event with Span (Correlation)</Text>
          <Text style={styles.description}>
            Event should be correlated with the span's trace ID and span ID
          </Text>
          
          <Button
            title="Track Event with Active Span"
            onPress={trackEventWithSpan}
            color="#2196F3"
          />
        </View>

        <View style={styles.infoBox}>
          <Text style={styles.infoText}>
            💡 Check your console logs and OpenTelemetry collector to see tracked events
          </Text>
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  fullContainer: { flex: 1 },
  container: {
    padding: 20,
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 20,
    textAlign: 'center',
  },
  section: {
    marginVertical: 14,
    padding: 16,
    backgroundColor: '#f5f5f5',
    borderRadius: 8,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 12,
    textAlign: 'center',
  },
  description: {
    fontSize: 12,
    color: '#888',
    textAlign: 'center',
    marginBottom: 12,
    fontStyle: 'italic',
    lineHeight: 16,
  },
  input: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 6,
    padding: 10,
    marginBottom: 10,
    backgroundColor: '#fff',
    fontSize: 14,
  },
  attributeInputRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  attributeKeyInput: {
    flex: 1,
    marginRight: 8,
  },
  attributeValueInput: {
    flex: 1,
    marginRight: 8,
  },
  addButton: {
    width: 40,
    height: 40,
    backgroundColor: '#2196F3',
    borderRadius: 20,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 10,
  },
  addButtonText: {
    color: '#fff',
    fontSize: 24,
    fontWeight: 'bold',
  },
  attributesList: {
    marginTop: 10,
    marginBottom: 16,
    padding: 12,
    backgroundColor: '#fff',
    borderRadius: 6,
    borderWidth: 1,
    borderColor: '#e0e0e0',
  },
  attributesTitle: {
    fontSize: 12,
    fontWeight: '600',
    marginBottom: 8,
    color: '#666',
  },
  attributeItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 6,
    borderBottomWidth: 1,
    borderBottomColor: '#f0f0f0',
  },
  attributeText: {
    fontSize: 13,
    color: '#333',
  },
  removeButton: {
    fontSize: 18,
    color: '#FF5722',
    paddingHorizontal: 8,
  },
  buttonRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  space: { 
    height: 10,
    width: 10,
  },
  infoBox: {
    marginTop: 20,
    padding: 16,
    backgroundColor: '#E3F2FD',
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: '#2196F3',
  },
  infoText: {
    fontSize: 13,
    color: '#1976D2',
    lineHeight: 20,
  },
});

