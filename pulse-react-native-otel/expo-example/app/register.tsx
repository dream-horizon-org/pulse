import { useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { ApiError, registerUser } from '../lib/api';

export default function RegisterScreen() {
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [firstname, setFirstname] = useState('');
  const [lastname, setLastname] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [doneId, setDoneId] = useState<number | null>(null);

  const submit = async () => {
    if (!email.trim() || !username.trim() || !password.trim()) {
      setError('Email, username, and password are required.');
      return;
    }
    setError(null);
    setBusy(true);
    try {
      const res = await registerUser({
        email: email.trim(),
        username: username.trim(),
        password,
        firstname: firstname.trim() || username.trim(),
        lastname: lastname.trim() || 'User',
      });
      setDoneId(res.id);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Sign up failed');
    } finally {
      setBusy(false);
    }
  };

  if (doneId !== null) {
    return (
      <View
        style={[
          styles.flex,
          { paddingTop: insets.top, paddingBottom: insets.bottom + 16 },
        ]}
      >
        <Text style={styles.title}>Account created</Text>
        <Text style={styles.body}>
          DummyJSON created user id <Text style={styles.bold}>{doneId}</Text>.
          Try signing in with the username and password you chose.
        </Text>
        <Pressable
          style={styles.primary}
          onPress={() => router.replace('/login')}
        >
          <Text style={styles.primaryText}>Go to sign in</Text>
        </Pressable>
      </View>
    );
  }

  return (
    <View
      style={[
        styles.flex,
        { paddingTop: insets.top, paddingBottom: insets.bottom + 16 },
      ]}
    >
      <Text style={styles.title}>Create account</Text>
      <Text style={styles.label}>Email</Text>
      <TextInput
        style={styles.input}
        value={email}
        onChangeText={setEmail}
        keyboardType="email-address"
        autoCapitalize="none"
      />
      <Text style={styles.label}>Username</Text>
      <TextInput
        style={styles.input}
        value={username}
        onChangeText={setUsername}
        autoCapitalize="none"
        autoCorrect={false}
      />
      <Text style={styles.label}>Password</Text>
      <TextInput
        style={styles.input}
        value={password}
        onChangeText={setPassword}
        secureTextEntry
      />
      <Text style={styles.label}>First name (optional)</Text>
      <TextInput
        style={styles.input}
        value={firstname}
        onChangeText={setFirstname}
      />
      <Text style={styles.label}>Last name (optional)</Text>
      <TextInput
        style={styles.input}
        value={lastname}
        onChangeText={setLastname}
      />
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <Pressable
        style={[styles.primary, busy && styles.disabled]}
        onPress={() => void submit()}
        disabled={busy}
      >
        {busy ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.primaryText}>Create account</Text>
        )}
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: '#fff', paddingHorizontal: 16 },
  title: { fontSize: 24, fontWeight: '800', marginBottom: 16 },
  body: { fontSize: 15, color: '#334155', lineHeight: 22, marginBottom: 24 },
  bold: { fontWeight: '800', color: '#0f172a' },
  label: { fontSize: 13, fontWeight: '600', marginBottom: 6 },
  input: {
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
    marginBottom: 12,
  },
  error: { color: '#dc2626', marginBottom: 8 },
  primary: {
    backgroundColor: '#2563eb',
    paddingVertical: 14,
    borderRadius: 10,
    alignItems: 'center',
    marginTop: 8,
  },
  primaryText: { color: '#fff', fontWeight: '700', fontSize: 16 },
  disabled: { opacity: 0.7 },
});
