import { useState } from 'react';
import {
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';

export default function CheckoutScreen() {
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const [name, setName] = useState('');
  const [address, setAddress] = useState('');
  const [email, setEmail] = useState('');
  const [shippingMethod, setShippingMethod] = useState<'standard' | 'express'>(
    'standard'
  );
  const [err, setErr] = useState<string | null>(null);

  const submit = () => {
    if (!name.trim() || !address.trim() || !email.trim()) {
      setErr('Please fill in all fields.');
      return;
    }
    setErr(null);
    router.push('/payment');
  };

  return (
    <ScrollView
      style={styles.flex}
      contentContainerStyle={[
        styles.scrollContent,
        { paddingBottom: insets.bottom + 24 },
      ]}
      keyboardShouldPersistTaps="handled"
    >
      <Text style={styles.title}>Shipping</Text>
      <Text style={styles.hint}>
        Details are kept on this device only (demo). Next step is a dummy card
        payment.
      </Text>
      <Text style={styles.label}>Full name</Text>
      <TextInput
        style={styles.input}
        value={name}
        onChangeText={setName}
        testID="shipping-name-input"
      />
      <Text style={styles.label}>Address</Text>
      <TextInput
        style={styles.input}
        value={address}
        onChangeText={setAddress}
        testID="shipping-address-input"
      />
      <Text style={styles.label}>Email</Text>
      <TextInput
        style={styles.input}
        value={email}
        onChangeText={setEmail}
        keyboardType="email-address"
        testID="shipping-email-input"
      />
      <View style={styles.methodBlock}>
        <Text style={styles.label}>Shipping method</Text>
        <Pressable
          style={styles.methodSelect}
          onPress={() =>
            setShippingMethod((current) =>
              current === 'standard' ? 'express' : 'standard'
            )
          }
          testID="shipping-method-select"
        >
          <Text style={styles.methodSelectText}>
            {shippingMethod === 'standard'
              ? 'Standard shipping'
              : 'Express shipping'}
          </Text>
          <Text style={styles.methodSelectMeta}>
            {shippingMethod === 'standard'
              ? '5-7 business days'
              : '2-3 business days'}
          </Text>
        </Pressable>
        <View style={styles.methodOptions}>
          <Pressable
            style={[
              styles.methodOption,
              shippingMethod === 'standard' && styles.methodOptionOn,
            ]}
            onPress={() => setShippingMethod('standard')}
            testID="standard-shipping-option"
          >
            <View>
              <Text style={styles.methodOptionTitle}>Standard shipping</Text>
              <Text style={styles.methodOptionBody}>
                Arrives in 5-7 business days
              </Text>
            </View>
            <Text style={styles.methodOptionPrice}>Free</Text>
          </Pressable>
          <Pressable
            style={[
              styles.methodOption,
              shippingMethod === 'express' && styles.methodOptionOn,
            ]}
            onPress={() => setShippingMethod('express')}
          >
            <View>
              <Text style={styles.methodOptionTitle}>Express shipping</Text>
              <Text style={styles.methodOptionBody}>
                Arrives in 2-3 business days
              </Text>
            </View>
            <Text style={styles.methodOptionPrice}>$9.99</Text>
          </Pressable>
        </View>
      </View>
      {err ? <Text style={styles.error}>{err}</Text> : null}
      <Pressable
        style={styles.primary}
        onPress={submit}
        testID="continue-to-payment-btn"
      >
        <Text style={styles.primaryText}>Continue to payment</Text>
      </Pressable>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: '#fff' },
  scrollContent: { padding: 16 },
  title: { fontSize: 22, fontWeight: '700', marginBottom: 8 },
  hint: { fontSize: 14, color: '#64748b', marginBottom: 20, lineHeight: 20 },
  label: { fontSize: 13, fontWeight: '600', marginBottom: 6, color: '#334155' },
  input: {
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
    marginBottom: 14,
  },
  methodBlock: { marginBottom: 8 },
  methodSelect: {
    borderWidth: 1,
    borderColor: '#cbd5e1',
    backgroundColor: '#f8fafc',
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
    marginBottom: 10,
  },
  methodSelectText: { fontSize: 15, fontWeight: '700', color: '#0f172a' },
  methodSelectMeta: { marginTop: 4, fontSize: 12, color: '#64748b' },
  methodOptions: { gap: 10 },
  methodOption: {
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 12,
    backgroundColor: '#fff',
  },
  methodOptionOn: {
    borderColor: '#2563eb',
    backgroundColor: '#eff6ff',
  },
  methodOptionTitle: { fontSize: 15, fontWeight: '700', color: '#0f172a' },
  methodOptionBody: { marginTop: 4, fontSize: 12, color: '#64748b' },
  methodOptionPrice: { fontSize: 14, fontWeight: '700', color: '#2563eb' },
  error: { color: '#dc2626', marginBottom: 12 },
  primary: {
    backgroundColor: '#2563eb',
    paddingVertical: 14,
    borderRadius: 10,
    alignItems: 'center',
    marginTop: 8,
  },
  primaryText: { color: '#fff', fontWeight: '700', fontSize: 16 },
});
