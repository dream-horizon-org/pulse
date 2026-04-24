import { Pressable, StyleSheet, Text, View, FlatList } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { useShop } from '../../context/ShopContext';

export default function AccountScreen() {
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const { username, token, logout, orders } = useShop();
  const signedIn = Boolean(token && username);

  return (
    <View style={[styles.flex, { paddingBottom: insets.bottom + 16 }]} testID="account-tab">
      <Text style={styles.heading}>Account</Text>
      {signedIn ? (
        <View style={styles.card}>
          <Text style={styles.label}>Signed in as</Text>
          <Text style={styles.value}>{username}</Text>
          <Pressable style={styles.secondary} onPress={() => void logout()}>
            <Text style={styles.secondaryText}>Sign out</Text>
          </Pressable>
        </View>
      ) : (
        <View style={styles.card}>
          <Text style={styles.body}>
            Sign in with a DummyJSON demo user, or create an account.
          </Text>
          <Pressable
            style={styles.primary}
            onPress={() => router.push('/login')}
          >
            <Text style={styles.primaryText}>Sign in</Text>
          </Pressable>
          <Pressable
            style={styles.link}
            onPress={() => router.push('/register')}
          >
            <Text style={styles.linkText}>Create account</Text>
          </Pressable>
        </View>
      )}
      <Text style={styles.subheading}>Order history</Text>
      <FlatList
        data={orders}
        keyExtractor={(o) => o.id}
        style={{ flex: 1 }}
        contentContainerStyle={{ paddingHorizontal: 12, paddingBottom: 24 }}
        renderItem={({ item }) => (
          <Pressable
            style={styles.orderRow}
            onPress={() => router.push(`/order-confirmation/${item.id}`)}
          >
            <View>
              <Text style={styles.orderId}>Order {item.id.slice(0, 12)}…</Text>
              <Text style={styles.orderDate}>
                {new Date(item.createdAt).toLocaleString()}
              </Text>
            </View>
            <Text style={styles.orderTotal}>${item.total.toFixed(2)}</Text>
          </Pressable>
        )}
        ItemSeparatorComponent={() => <View style={styles.sep} />}
        ListEmptyComponent={
          <Text style={styles.empty}>
            No orders yet. Complete a checkout to see history here.
          </Text>
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: '#fff' },
  heading: {
    fontSize: 22,
    fontWeight: '700',
    paddingHorizontal: 12,
    marginBottom: 12,
  },
  subheading: {
    fontSize: 16,
    fontWeight: '700',
    paddingHorizontal: 12,
    marginTop: 20,
    marginBottom: 8,
  },
  card: {
    marginHorizontal: 12,
    padding: 16,
    backgroundColor: '#f8fafc',
    borderRadius: 12,
    gap: 12,
  },
  label: { fontSize: 12, color: '#64748b', textTransform: 'uppercase' },
  value: { fontSize: 18, fontWeight: '700' },
  body: { fontSize: 15, color: '#334155', lineHeight: 22 },
  primary: {
    backgroundColor: '#2563eb',
    paddingVertical: 12,
    borderRadius: 8,
    alignItems: 'center',
  },
  primaryText: { color: '#fff', fontWeight: '700' },
  secondary: {
    alignSelf: 'flex-start',
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#cbd5e1',
  },
  secondaryText: { fontWeight: '600', color: '#334155' },
  link: { alignSelf: 'center', paddingVertical: 4 },
  linkText: { color: '#2563eb', fontWeight: '600' },
  orderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 4,
  },
  orderId: { fontWeight: '600', marginBottom: 4 },
  orderDate: { fontSize: 12, color: '#64748b' },
  orderTotal: { fontSize: 16, fontWeight: '700', color: '#2563eb' },
  sep: { height: StyleSheet.hairlineWidth, backgroundColor: '#e2e8f0' },
  empty: { color: '#64748b', marginTop: 8 },
});
