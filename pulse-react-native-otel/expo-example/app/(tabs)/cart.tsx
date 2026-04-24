import { Pressable, StyleSheet, Text, View, FlatList } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { ProductImage } from '../../components/ProductImage';
import { useShop } from '../../context/ShopContext';

export default function CartScreen() {
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const { cart, cartTotal, setLineQuantity, removeFromCart } = useShop();

  return (
    <View style={styles.flex}>
      <Text style={styles.heading}>Cart</Text>
      <FlatList
        data={cart}
        keyExtractor={(item) => String(item.productId)}
        contentContainerStyle={{ padding: 12, flexGrow: 1 }}
        renderItem={({ item }) => (
          <View style={styles.row}>
            <ProductImage uri={item.image} style={styles.thumb} />
            <View style={styles.body}>
              <Text numberOfLines={2} style={styles.title}>
                {item.title}
              </Text>
              <Text style={styles.lineTotal}>
                ${(item.price * item.quantity).toFixed(2)}
              </Text>
              <View style={styles.qtyRow}>
                <Text style={styles.qtyLabel}>Qty</Text>
                <Pressable
                  style={styles.qtyBtn}
                  onPress={() =>
                    void setLineQuantity(item.productId, item.quantity - 1)
                  }
                >
                  <Text style={styles.qtyBtnText}>−</Text>
                </Pressable>
                <Text style={styles.qtyVal}>{item.quantity}</Text>
                <Pressable
                  style={styles.qtyBtn}
                  onPress={() =>
                    void setLineQuantity(item.productId, item.quantity + 1)
                  }
                >
                  <Text style={styles.qtyBtnText}>+</Text>
                </Pressable>
                <Pressable
                  style={styles.remove}
                  onPress={() => void removeFromCart(item.productId)}
                >
                  <Text style={styles.removeText}>Remove</Text>
                </Pressable>
              </View>
            </View>
          </View>
        )}
        ItemSeparatorComponent={() => <View style={{ height: 14 }} />}
        ListEmptyComponent={
          <Text style={styles.empty}>Your cart is empty.</Text>
        }
      />
      {cart.length > 0 ? (
        <View style={[styles.footer, { paddingBottom: insets.bottom + 12 }]}>
          <Text style={styles.total}>Total: ${cartTotal.toFixed(2)}</Text>
          <Pressable
            style={styles.checkout}
            testID="proceed-to-checkout-btn"
            onPress={() => router.push('/checkout')}
          >
            <Text style={styles.checkoutText}>Checkout</Text>
          </Pressable>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: '#fff' },
  heading: {
    fontSize: 22,
    fontWeight: '700',
    paddingHorizontal: 12,
    marginBottom: 8,
  },
  row: {
    flexDirection: 'row',
    gap: 12,
    backgroundColor: '#f8fafc',
    borderRadius: 12,
    padding: 10,
  },
  thumb: { width: 72, height: 72, borderRadius: 8, backgroundColor: '#e2e8f0' },
  body: { flex: 1 },
  title: { fontSize: 15, fontWeight: '600', marginBottom: 4 },
  lineTotal: {
    fontSize: 15,
    fontWeight: '700',
    color: '#2563eb',
    marginBottom: 8,
  },
  qtyRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  qtyLabel: { color: '#64748b' },
  qtyBtn: {
    width: 36,
    height: 36,
    borderRadius: 8,
    backgroundColor: '#e2e8f0',
    alignItems: 'center',
    justifyContent: 'center',
  },
  qtyBtnText: {
    fontSize: 20,
    fontWeight: '700',
    color: '#0f172a',
    lineHeight: 22,
  },
  qtyVal: {
    minWidth: 28,
    textAlign: 'center',
    fontSize: 16,
    fontWeight: '700',
  },
  remove: { marginLeft: 'auto' },
  removeText: { color: '#dc2626', fontWeight: '600' },
  empty: { textAlign: 'center', color: '#64748b', marginTop: 48 },
  footer: {
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#e2e8f0',
    paddingHorizontal: 16,
    paddingTop: 12,
    backgroundColor: '#fff',
  },
  total: { fontSize: 18, fontWeight: '700', marginBottom: 12 },
  checkout: {
    backgroundColor: '#2563eb',
    paddingVertical: 14,
    borderRadius: 10,
    alignItems: 'center',
  },
  checkoutText: { color: '#fff', fontWeight: '700', fontSize: 16 },
});
