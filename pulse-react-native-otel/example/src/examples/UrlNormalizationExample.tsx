import { useState } from 'react';
import { View, Text, Button, StyleSheet, ScrollView, Alert, TextInput } from 'react-native';

export default function UrlNormalizationExample() {
  const [loading, setLoading] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<string | null>(null);
  const [customUrl, setCustomUrl] = useState('');

  const showResult = (message: string, isError = false) => {
    setLastResult(message);
    console.log(`[Pulse URL Normalization] ${message}`);
    if (isError) {
      Alert.alert('Error', message);
    } else {
      Alert.alert('Success', message);
    }
  };

  const testRequest = async (url: string, description: string) => {
    setLoading(description);
    try {
      await fetch(url);
      showResult(`${description}: Request sent`);
    } catch (error: any) {
      showResult(`${description}: ${error.message}`, true);
    } finally {
      setLoading(null);
    }
  };

  const testUuid = () => testRequest(
    'https://jsonplaceholder.typicode.com/users/550e8400-e29b-41d4-a716-446655440000/posts?page=1&limit=10',
    'UUID + Query'
  );

  const testNumericId = () => testRequest(
    'https://jsonplaceholder.typicode.com/users/12345/posts',
    'Numeric ID'
  );

  const testMongoId = () => testRequest(
    'https://jsonplaceholder.typicode.com/users/507f1f77bcf86cd799439011',
    'MongoDB ObjectId'
  );

  const testGitHash = () => testRequest(
    'https://jsonplaceholder.typicode.com/commits/abc123def456789012345678901234567890abcd',
    'Git Commit Hash'
  );

  const testLongAlphanumeric = () => testRequest(
    'https://jsonplaceholder.typicode.com/users/abc123def456ghi789jkl012',
    'Long Alphanumeric'
  );

  const testMultipleIds = () => testRequest(
    'https://jsonplaceholder.typicode.com/users/550e8400-e29b-41d4-a716-446655440000/posts/12345/comments/507f1f77bcf86cd799439011?page=1',
    'Multiple IDs'
  );

  const testCustomUrl = () => {
    if (!customUrl.trim()) {
      Alert.alert('Error', 'Please enter a URL');
      return;
    }
    testRequest(customUrl, 'Custom URL');
  };

  return (
    <View style={styles.fullContainer}>
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.title}>URL Normalization Testing</Text>
        <Text style={styles.description}>
          Test URL normalization with various identifier patterns. Check span attributes to see normalized URLs.
        </Text>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Standard Tests</Text>
          
          <Button
            title={loading === 'UUID + Query' ? 'Loading...' : 'UUID + Query Params'}
            onPress={testUuid}
            disabled={loading !== null}
            color="#673AB7"
          />
          <View style={styles.space} />
          
          <Button
            title={loading === 'Numeric ID' ? 'Loading...' : 'Numeric ID (12345)'}
            onPress={testNumericId}
            disabled={loading !== null}
            color="#673AB7"
          />
          <View style={styles.space} />
          
          <Button
            title={loading === 'MongoDB ObjectId' ? 'Loading...' : 'MongoDB ObjectId'}
            onPress={testMongoId}
            disabled={loading !== null}
            color="#673AB7"
          />
          <View style={styles.space} />
          
          <Button
            title={loading === 'Git Commit Hash' ? 'Loading...' : 'Git Commit Hash (40 chars)'}
            onPress={testGitHash}
            disabled={loading !== null}
            color="#673AB7"
          />
          <View style={styles.space} />
          
          <Button
            title={loading === 'Long Alphanumeric' ? 'Loading...' : 'Long Alphanumeric (16+ chars)'}
            onPress={testLongAlphanumeric}
            disabled={loading !== null}
            color="#673AB7"
          />
          <View style={styles.space} />
          
          <Button
            title={loading === 'Multiple IDs' ? 'Loading...' : 'Multiple IDs Combined'}
            onPress={testMultipleIds}
            disabled={loading !== null}
            color="#673AB7"
          />
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Custom URL Test</Text>
          <TextInput
            style={styles.input}
            placeholder="Enter URL to test (e.g., https://api.example.com/users/12345)"
            value={customUrl}
            onChangeText={setCustomUrl}
            autoCapitalize="none"
            autoCorrect={false}
          />
          <View style={styles.space} />
          <Button
            title={loading === 'Custom URL' ? 'Loading...' : 'Test Custom URL'}
            onPress={testCustomUrl}
            disabled={loading !== null}
            color="#9C27B0"
          />
        </View>

        {lastResult && (
          <View style={styles.resultBox}>
            <Text style={styles.resultTitle}>Last Result:</Text>
            <Text style={styles.resultText}>{lastResult}</Text>
          </View>
        )}

        <View style={styles.infoBox}>
          <Text style={styles.infoText}>
            🔍 All identifiers are replaced with {'[redacted]'} in span attributes
          </Text>
          <Text style={styles.infoText}>
            📊 Check your telemetry dashboard to see normalized URLs
          </Text>
          <Text style={styles.infoText}>
            ✅ Query parameters are automatically removed
          </Text>
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  fullContainer: {
    flex: 1,
  },
  container: {
    padding: 20,
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 8,
    textAlign: 'center',
  },
  description: {
    fontSize: 13,
    color: '#666',
    textAlign: 'center',
    marginBottom: 20,
    fontStyle: 'italic',
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
  space: {
    height: 10,
  },
  input: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 6,
    padding: 12,
    fontSize: 14,
    backgroundColor: '#fff',
  },
  resultBox: {
    marginTop: 16,
    padding: 12,
    backgroundColor: '#E8F5E9',
    borderRadius: 6,
    borderLeftWidth: 4,
    borderLeftColor: '#4CAF50',
  },
  resultTitle: {
    fontSize: 12,
    fontWeight: '600',
    marginBottom: 4,
    color: '#2E7D32',
  },
  resultText: {
    fontSize: 13,
    color: '#1B5E20',
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
    fontSize: 12,
    color: '#1976D2',
    lineHeight: 18,
    marginBottom: 4,
  },
});

