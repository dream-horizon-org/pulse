import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { ProductImage } from '../../components/ProductImage';
import { useShop } from '../../context/ShopContext';

export default function WishlistScreen() {
  const router = useRouter();
  const { wishlist, toggleWishlist } = useShop();

  return (
    <View style={styles.flex}>
      <Text style={styles.heading}>Wishlist</Text>
      <FlatList
        data={wishlist}
        keyExtractor={(item) => String(item.productId)}
        contentContainerStyle={{ padding: 12, flexGrow: 1 }}
        renderItem={({ item }) => (
          <View style={styles.card}>
            <Pressable
              onPress={() => router.push(`/product/${item.productId}`)}
            >
              <ProductImage uri={item.image} style={styles.thumb} />
            </Pressable>
            <View style={styles.body}>
              <Pressable
                onPress={() => router.push(`/product/${item.productId}`)}
              >
                <Text numberOfLines={2} style={styles.title}>
                  {item.title}
                </Text>
              </Pressable>
              <Text style={styles.price}>${item.price.toFixed(2)}</Text>
              <Pressable
                style={styles.remove}
                onPress={() => void toggleWishlist(item)}
              >
                <Text style={styles.removeText}>Remove</Text>
              </Pressable>
            </View>
          </View>
        )}
        ItemSeparatorComponent={() => <View style={{ height: 12 }} />}
        ListEmptyComponent={
          <Text style={styles.empty}>Save items from a product page.</Text>
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
    marginBottom: 8,
  },
  card: {
    flexDirection: 'row',
    gap: 12,
    backgroundColor: '#f8fafc',
    borderRadius: 12,
    padding: 10,
  },
  thumb: { width: 80, height: 80, borderRadius: 8, backgroundColor: '#e2e8f0' },
  body: { flex: 1, justifyContent: 'center' },
  title: { fontSize: 16, fontWeight: '600', marginBottom: 4 },
  price: { fontSize: 16, fontWeight: '700', color: '#2563eb', marginBottom: 8 },
  remove: { alignSelf: 'flex-start' },
  removeText: { color: '#dc2626', fontWeight: '600' },
  empty: { textAlign: 'center', color: '#64748b', marginTop: 48 },
});
