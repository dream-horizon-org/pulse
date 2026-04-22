import { Pressable, StyleSheet, Text, View, FlatList } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useShop } from '../../context/ShopContext';

export default function OrderConfirmationScreen() {
  const { orderId } = useLocalSearchParams<{ orderId: string }>();
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const { orders } = useShop();
  const order = orders.find((o) => o.id === orderId);

  if (!order) {
    return (
      <View
        style={[
          styles.center,
          { paddingTop: insets.top, paddingHorizontal: 24 },
        ]}
      >
        <Text style={styles.title}>Order not found</Text>
        <Pressable
          style={styles.primary}
          onPress={() => router.replace('/(tabs)/account')}
        >
          <Text style={styles.primaryText}>Account</Text>
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
      <Text style={styles.title}>Thank you!</Text>
      <Text style={styles.sub}>Order ID</Text>
      <Text selectable style={styles.id}>
        {order.id}
      </Text>
      <Text style={styles.total}>Total paid: ${order.total.toFixed(2)}</Text>
      <Text style={styles.section}>Items</Text>
      <FlatList
        data={order.lines}
        keyExtractor={(l) => String(l.productId)}
        renderItem={({ item }) => (
          <View style={styles.line}>
            <Text style={styles.lineTitle} numberOfLines={2}>
              {item.title} × {item.quantity}
            </Text>
            <Text style={styles.linePrice}>
              ${(item.price * item.quantity).toFixed(2)}
            </Text>
          </View>
        )}
        ItemSeparatorComponent={() => <View style={styles.sep} />}
      />
      <Pressable
        style={styles.primary}
        onPress={() => router.replace('/(tabs)')}
      >
        <Text style={styles.primaryText}>Continue shopping</Text>
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
  title: { fontSize: 24, fontWeight: '800', marginBottom: 16 },
  sub: { fontSize: 12, color: '#64748b', textTransform: 'uppercase' },
  id: { fontSize: 14, fontFamily: 'monospace', marginBottom: 12 },
  total: { fontSize: 18, fontWeight: '700', marginBottom: 20 },
  section: { fontSize: 16, fontWeight: '700', marginBottom: 8 },
  line: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 12,
    paddingVertical: 8,
  },
  lineTitle: { flex: 1, fontSize: 15 },
  linePrice: { fontWeight: '700' },
  sep: { height: StyleSheet.hairlineWidth, backgroundColor: '#e2e8f0' },
  primary: {
    marginTop: 24,
    backgroundColor: '#2563eb',
    paddingVertical: 14,
    borderRadius: 10,
    alignItems: 'center',
  },
  primaryText: { color: '#fff', fontWeight: '700', fontSize: 16 },
});
