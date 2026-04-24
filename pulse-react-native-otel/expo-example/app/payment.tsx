import { useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { useShop } from '../context/ShopContext';

export default function PaymentScreen() {
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const { cart, cartTotal, placeOrder } = useShop();
  const [busy, setBusy] = useState(false);

  const pay = async () => {
    if (cart.length === 0) {
      router.replace('/(tabs)/cart');
      return;
    }
    setBusy(true);
    try {
      const orderId = await placeOrder(cart);
      router.replace(`/order-confirmation/${orderId}`);
    } finally {
      setBusy(false);
    }
  };

  if (cart.length === 0) {
    return (
      <View
        style={[
          styles.center,
          { paddingTop: insets.top, paddingHorizontal: 24 },
        ]}
      >
        <Text style={styles.title}>Cart is empty</Text>
        <Pressable
          style={styles.primary}
          onPress={() => router.replace('/(tabs)/cart')}
        >
          <Text style={styles.primaryText}>Back to cart</Text>
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
      <Text style={styles.title}>Dummy payment</Text>
      <Text style={styles.body}>
        No card data is collected. Tap pay to complete your order for{' '}
        <Text style={styles.bold}>${cartTotal.toFixed(2)}</Text>.
      </Text>
      <View testID="order-summary" style={styles.summary}>
        <Text style={styles.summaryText}>Order Summary</Text>
        <Text style={styles.total}>Total: ${cartTotal.toFixed(2)}</Text>
      </View>
      
      <Pressable
        style={[styles.primary, busy && styles.disabled]}
        onPress={() => void pay()}
        disabled={busy}
        testID="place-order-btn"
      >
        {busy ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.primaryText}>Pay now</Text>
        )}
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: '#fff', paddingHorizontal: 16 },
  center: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#fff',
  },
  title: { fontSize: 22, fontWeight: '700', marginBottom: 12 },
  body: { fontSize: 16, color: '#334155', lineHeight: 24, marginBottom: 24 },
  bold: { fontWeight: '800', color: '#0f172a' },
  input: { height: 0, opacity: 0 },
  summary: {
    backgroundColor: '#f8fafc',
    borderRadius: 10,
    padding: 16,
    marginBottom: 20,
  },
  summaryText: { fontSize: 14, fontWeight: '600', marginBottom: 8 },
  total: { fontSize: 18, fontWeight: '700', color: '#2563eb' },
  primary: {
    backgroundColor: '#16a34a',
    paddingVertical: 14,
    borderRadius: 10,
    alignItems: 'center',
  },
  primaryText: { color: '#fff', fontWeight: '700', fontSize: 16 },
  disabled: { opacity: 0.7 },
});
