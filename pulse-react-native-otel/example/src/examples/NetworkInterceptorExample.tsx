import { useState } from 'react';
import { View, Text, Button, StyleSheet, ScrollView, Alert } from 'react-native';
import axios from 'axios';

export default function NetworkInterceptorDemo() {
  const [loading, setLoading] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<string | null>(null);

  const showResult = (message: string, isError = false) => {
    setLastResult(message);
    console.log(`[Pulse Network] ${message}`);
    if (isError) {
      Alert.alert('Network Error', message);
    } else {
      Alert.alert('Success', message);
    }
  };

  // Fetch GET request
  const testFetchGet = async () => {
    setLoading('fetch-get');
    try {
      console.log('[Pulse Network] 🌐 Testing fetch() GET request...');
      const response = await fetch('https://jsonplaceholder.typicode.com/posts/1');
      const data = await response.json();
      showResult(`Fetch GET: ${data.title}`);
    } catch (error: any) {
      showResult(`Fetch GET Error: ${error.message}`, true);
    } finally {
      setLoading(null);
    }
  };

  // Fetch POST request
  const testFetchPost = async () => {
    setLoading('fetch-post');
    try {
      console.log('[Pulse Network] 🌐 Testing fetch() POST request...');
      const response = await fetch('https://jsonplaceholder.typicode.com/posts', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          title: 'Test Post from Pulse SDK',
          body: 'This is a test POST request from Pulse React Native SDK',
          userId: 1,
        }),
      });
      const data = await response.json();
      showResult(`Fetch POST: Created post #${data.id}`);
    } catch (error: any) {
      showResult(`Fetch POST Error: ${error.message}`, true);
    } finally {
      setLoading(null);
    }
  };

  // XHR GET request
  const testXhrGet = () => {
    setLoading('xhr-get');
    try {
      console.log('[Pulse Network] 🌐 Testing XMLHttpRequest GET...');
      const xhr = new XMLHttpRequest();
      xhr.open('GET', 'https://jsonplaceholder.typicode.com/comments/1');
      xhr.onload = () => {
        if (xhr.status === 200) {
          const data = JSON.parse(xhr.responseText);
          showResult(`XHR GET: ${data.name}`);
        } else {
          showResult(`XHR GET Error: Status ${xhr.status}`, true);
        }
        setLoading(null);
      };
      xhr.onerror = () => {
        showResult('XHR GET Error: Network error', true);
        setLoading(null);
      };
      xhr.send();
    } catch (error: any) {
      showResult(`XHR GET Error: ${error.message}`, true);
      setLoading(null);
    }
  };

  // Axios GET request
  const testAxiosGet = async () => {
    setLoading('axios-get');
    try {
      console.log('[Pulse Network] 🌐 Testing axios GET request...');
      const response = await axios.get('https://jsonplaceholder.typicode.com/users/1');
      showResult(`Axios GET: ${response.data.name}`);
    } catch (error: any) {
      showResult(`Axios GET Error: ${error.message}`, true);
    } finally {
      setLoading(null);
    }
  };

  // Axios POST request
  const testAxiosPost = async () => {
    setLoading('axios-post');
    try {
      console.log('[Pulse Network] 🌐 Testing axios POST request...');
      const response = await axios.post(
        'https://jsonplaceholder.typicode.com/posts',
        {
          title: 'Test Post from Axios',
          body: 'This is a test POST request using axios',
          userId: 1,
        },
        {
          headers: {
            'Content-Type': 'application/json',
          },
        }
      );
      showResult(`Axios POST: Created post #${response.data.id}`);
    } catch (error: any) {
      showResult(`Axios POST Error: ${error.message}`, true);
    } finally {
      setLoading(null);
    }
  };

  // Fetch with 404 error
  const testFetch404 = async () => {
    setLoading('fetch-404');
    try {
      console.log('[Pulse Network] 🌐 Testing fetch() with 404 error...');
      const response = await fetch('https://jsonplaceholder.typicode.com/posts/999999');
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      const data = await response.json();
      showResult(`Fetch 404: ${data.title}`);
    } catch (error: any) {
      showResult(`Fetch 404 Error: ${error.message}`, true);
    } finally {
      setLoading(null);
    }
  };

  // Fetch with network error (invalid domain)
  const testFetchNetworkError = async () => {
    setLoading('fetch-network-error');
    try {
      console.log('[Pulse Network] 🌐 Testing fetch() with network error...');
      const response = await fetch('https://this-domain-should-not-exist-12345.com/data');
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      const data = await response.json();
      showResult(`Fetch Network Error: ${data}`);
    } catch (error: any) {
      showResult(`Network Error: ${error.message}`, true);
    } finally {
      setLoading(null);
    }
  };

  // Axios with error
  const testAxiosError = async () => {
    setLoading('axios-error');
    try {
      console.log('[Pulse Network] 🌐 Testing axios with error...');
      const response = await axios.get('https://jsonplaceholder.typicode.com/posts/999999');
      showResult(`Axios Error: ${response.data.title}`);
    } catch (error: any) {
      if (error.response) {
        showResult(`Axios Error: Status ${error.response.status}`, true);
      } else {
        showResult(`Axios Error: ${error.message}`, true);
      }
    } finally {
      setLoading(null);
    }
  };

  // Multiple sequential requests
  const testMultipleRequests = async () => {
    setLoading('multiple');
    try {
      console.log('[Pulse Network] 🌐 Testing multiple sequential requests...');
      
      // Request 1: Fetch GET
      const fetchResponse = await fetch('https://jsonplaceholder.typicode.com/posts/1');
      const fetchData = await fetchResponse.json();
      console.log('[Pulse Network] Request 1 completed:', fetchData.title);
      
      // Request 2: Axios GET
      const axiosResponse = await axios.get('https://jsonplaceholder.typicode.com/users/1');
      console.log('[Pulse Network] Request 2 completed:', axiosResponse.data.name);
      
      // Request 3: Fetch POST
      const postResponse = await fetch('https://jsonplaceholder.typicode.com/posts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title: 'Multi-test', body: 'Testing', userId: 1 }),
      });
      const postData = await postResponse.json();
      console.log('[Pulse Network] Request 3 completed:', postData.id);
      
      showResult('Multiple requests completed successfully!');
    } catch (error: any) {
      showResult(`Multiple Requests Error: ${error.message}`, true);
    } finally {
      setLoading(null);
    }
  };

  // URL Normalization test
  const testUrlNormalization = async () => {
    setLoading('url-normalization');
    try {
      console.log('[Pulse Network] 🧪 Testing URL normalization...');
      
      // Test URL with query parameters and UUID
      const testUrl = 'https://jsonplaceholder.typicode.com/users/550e8400-e29b-41d4-a716-446655440000/posts?page=1&limit=10&sort=date';
      
      console.log('[Pulse Network] Original URL:', testUrl);
      console.log('[Pulse Network] Expected normalized URL: https://jsonplaceholder.typicode.com/users/{uuid}/posts');
      
      const response = await fetch(testUrl);
      const data = await response.json();
      
      console.log('[Pulse Network] ✅ Request completed');
      console.log('[Pulse Network] 📝 Check span attributes in Pulse UI or Logcat');
      console.log('[Pulse Network] 📝 The http.url attribute should show normalized URL without query params and with {uuid}');
      
      showResult('URL Normalization Test: Check logs/Pulse UI for normalized URL in span attributes');
    } catch (error: any) {
      showResult(`URL Normalization Error: ${error.message}`, true);
    } finally {
      setLoading(null);
    }
  };

  return (
    <View style={styles.fullContainer}>
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.title}>Network Interceptor Demo</Text>
        <Text style={styles.description}>
          Test different network calls to verify OpenTelemetry instrumentation
        </Text>

        {/* Fetch Requests Section */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Fetch API</Text>
          
          <Button
            title={loading === 'fetch-get' ? 'Loading...' : 'Fetch GET Request'}
            onPress={testFetchGet}
            disabled={loading !== null}
            color="#2196F3"
          />
          <View style={styles.space} />
          
          <Button
            title={loading === 'fetch-post' ? 'Loading...' : 'Fetch POST Request'}
            onPress={testFetchPost}
            disabled={loading !== null}
            color="#2196F3"
          />
        </View>

        {/* XMLHttpRequest Section */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>XMLHttpRequest</Text>
          
          <Button
            title={loading === 'xhr-get' ? 'Loading...' : 'XHR GET Request'}
            onPress={testXhrGet}
            disabled={loading !== null}
            color="#FF9800"
          />
        </View>

        {/* Axios Section */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Axios</Text>
          
          <Button
            title={loading === 'axios-get' ? 'Loading...' : 'Axios GET Request'}
            onPress={testAxiosGet}
            disabled={loading !== null}
            color="#4CAF50"
          />
          <View style={styles.space} />
          
          <Button
            title={loading === 'axios-post' ? 'Loading...' : 'Axios POST Request'}
            onPress={testAxiosPost}
            disabled={loading !== null}
            color="#4CAF50"
          />
        </View>

        {/* Error Scenarios Section */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Error Scenarios</Text>
          
          <Button
            title={loading === 'fetch-404' ? 'Loading...' : 'Fetch 404 Error'}
            onPress={testFetch404}
            disabled={loading !== null}
            color="#F44336"
          />
          <View style={styles.space} />
          
          <Button
            title={loading === 'fetch-network-error' ? 'Loading...' : 'Fetch Network Error'}
            onPress={testFetchNetworkError}
            disabled={loading !== null}
            color="#F44336"
          />
          <View style={styles.space} />
          
          <Button
            title={loading === 'axios-error' ? 'Loading...' : 'Axios Error'}
            onPress={testAxiosError}
            disabled={loading !== null}
            color="#F44336"
          />
        </View>

        {/* Advanced Section */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Advanced</Text>
          
          <Button
            title={loading === 'multiple' ? 'Loading...' : 'Multiple Sequential Requests'}
            onPress={testMultipleRequests}
            disabled={loading !== null}
            color="#9C27B0"
          />
          <View style={styles.space} />
          
          <Button
            title={loading === 'url-normalization' ? 'Loading...' : 'Test URL Normalization'}
            onPress={testUrlNormalization}
            disabled={loading !== null}
            color="#9C27B0"
          />
        </View>

        {/* Result Display */}
        {lastResult && (
          <View style={styles.resultBox}>
            <Text style={styles.resultTitle}>Last Result:</Text>
            <Text style={styles.resultText}>{lastResult}</Text>
          </View>
        )}

        {/* Info Box */}
        <View style={styles.infoBox}>
          <Text style={styles.infoText}>
            💡 Check Android Logcat for [Pulse Network] logs and OpenTelemetry telemetry data
          </Text>
          <Text style={styles.infoText}>
            All network requests should be automatically instrumented and tracked
          </Text>
          <Text style={styles.infoText}>
            🔍 URL Normalization: URLs are normalized (query params removed, UUIDs replaced with {'{uuid}'}) in span attributes
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
