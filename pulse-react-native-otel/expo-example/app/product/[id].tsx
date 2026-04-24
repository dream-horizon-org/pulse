import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { useCallback, useEffect, useLayoutEffect, useState } from 'react';
import {
  ActivityIndicator,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { ProductImage } from '../../components/ProductImage';
import { useShop } from '../../context/ShopContext';
import { ApiError, getProduct } from '../../shared/api';
import { labelFromCategorySlug } from '../../shared/formatCategory';
import { theme } from '../../shared/theme';
import type { Product } from '../../shared/types';

type CartFeedback = { lineQty: number; cartItems: number };

export default function ProductScreen() {
  const { id: idParam } = useLocalSearchParams<{ id: string }>();
  const id = Number(idParam);
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const navigation = useNavigation();
  const {
    addToCart,
    toggleWishlist,
    isWishlisted,
    recordProductView,
    cartCount,
  } = useShop();
  const [product, setProduct] = useState<Product | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [cartFeedback, setCartFeedback] = useState<CartFeedback | null>(null);

  const goToCart = useCallback(() => {
    setCartFeedback(null);
    router.dismissTo('/(tabs)/cart');
  }, [router]);

  useLayoutEffect(() => {
    navigation.setOptions({
      headerRight: () => (
        <Pressable
          onPress={goToCart}
          style={styles.headerCartWrap}
          hitSlop={10}
          accessibilityRole="button"
          accessibilityLabel="Open shopping bag"
        >
          <Ionicons name="cart-outline" size={24} color={theme.primaryDark} />
          {cartCount > 0 ? (
            <View style={styles.headerBadge}>
              <Text style={styles.headerBadgeText}>
                {cartCount > 99 ? '99+' : cartCount}
              </Text>
            </View>
          ) : null}
        </Pressable>
      ),
    });
  }, [navigation, cartCount, goToCart]);

  const load = useCallback(async () => {
    if (!Number.isFinite(id) || id <= 0) {
      setError('Invalid product');
      setLoading(false);
      return;
    }
    setError(null);
    try {
      const p = await getProduct(id);
      setProduct(p);
      void recordProductView(id);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong');
    } finally {
      setLoading(false);
    }
  }, [id, recordProductView]);

  useEffect(() => {
    setLoading(true);
    void load();
  }, [load]);

  if (loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color={theme.primary} />
      </View>
    );
  }

  if (error || !product) {
    return (
      <View style={[styles.center, { paddingHorizontal: 24 }]}>
        <Text style={styles.errorTitle}>Product unavailable</Text>
        <Text style={styles.errorBody}>{error ?? 'Not found'}</Text>
        <Pressable style={styles.retry} onPress={() => void load()}>
          <Text style={styles.retryText}>Retry</Text>
        </Pressable>
        <Pressable style={styles.back} onPress={() => router.back()}>
          <Text style={styles.backText}>Go back</Text>
        </Pressable>
      </View>
    );
  }

  const wish = isWishlisted(product.id);
  const snapshot = {
    productId: product.id,
    title: product.title,
    price: product.price,
    image: product.image,
  };

  return (
    <>
      <ScrollView
        style={styles.flex}
        contentContainerStyle={{
          paddingBottom: insets.bottom + 24,
          paddingHorizontal: 16,
          paddingTop: 12,
        }}
      >
        <ProductImage uri={product.image} style={styles.hero} />
        <Text style={styles.title}>{product.title}</Text>
        <Text style={styles.price}>${product.price.toFixed(2)}</Text>
        <Text style={styles.cat}>
          {labelFromCategorySlug(product.category)}
        </Text>
        {product.rating ? (
          <Text style={styles.rating}>
            ★ {product.rating.rate} ({product.rating.count} reviews)
          </Text>
        ) : null}
        <Text style={styles.desc}>{product.description}</Text>
        <View style={styles.actions}>
          <Pressable
            style={({ pressed }) => [styles.primary, pressed && styles.pressed]}
            onPress={() =>
              void (async () => {
                const res = await addToCart({
                  productId: product.id,
                  title: product.title,
                  price: product.price,
                  image: product.image,
                });
                setCartFeedback({
                  lineQty: res.lineQty,
                  cartItems: res.cartItems,
                });
              })()
            }
            testID="add-to-cart-btn"
          >
            <Text style={styles.primaryText}>Add to cart</Text>
          </Pressable>
          <Pressable
            style={[styles.secondary, wish && styles.secondaryOn]}
            onPress={() => void toggleWishlist(snapshot)}
          >
            <Text
              style={[styles.secondaryText, wish && styles.secondaryTextOn]}
            >
              {wish ? 'Saved' : 'Wishlist'}
            </Text>
          </Pressable>
        </View>
      </ScrollView>

      <Modal
        visible={cartFeedback !== null}
        transparent
        animationType="fade"
        onRequestClose={() => setCartFeedback(null)}
      >
        <Pressable
          style={styles.modalBackdrop}
          onPress={() => setCartFeedback(null)}
        >
          <Pressable
            style={styles.modalCard}
            onPress={(e) => e.stopPropagation()}
          >
            <View style={styles.modalIconCircle}>
              <Ionicons
                name="checkmark-circle"
                size={40}
                color={theme.primary}
              />
            </View>
            <Text style={styles.modalTitle}>Added to bag</Text>
            {cartFeedback ? (
              <Text style={styles.modalBody}>
                {cartFeedback.lineQty} of this item in your bag ·{' '}
                {cartFeedback.cartItems}{' '}
                {cartFeedback.cartItems === 1 ? 'item' : 'items'} total
              </Text>
            ) : null}
            <View style={styles.modalActions}>
              <Pressable
                style={[styles.modalBtn, styles.modalBtnGhost]}
                onPress={() => setCartFeedback(null)}
              >
                <Text style={styles.modalBtnGhostText}>Continue shopping</Text>
              </Pressable>
              <Pressable
                style={[styles.modalBtn, styles.modalBtnSolid]}
                onPress={goToCart}
                testID="view-bag-btn"
              >
                <Text style={styles.modalBtnSolidText}>View bag</Text>
              </Pressable>
            </View>
          </Pressable>
        </Pressable>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: theme.bg },
  center: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: theme.bg,
  },
  headerCartWrap: {
    marginRight: 4,
    padding: 6,
    justifyContent: 'center',
    alignItems: 'center',
  },
  headerBadge: {
    position: 'absolute',
    top: -2,
    right: -2,
    minWidth: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: theme.accent,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 4,
    borderWidth: 2,
    borderColor: theme.surface,
  },
  headerBadgeText: {
    color: '#1c1917',
    fontSize: 10,
    fontWeight: '800',
  },
  hero: {
    width: '100%',
    height: 280,
    borderRadius: 12,
    backgroundColor: '#e2e8f0',
    marginBottom: 16,
  },
  title: { fontSize: 22, fontWeight: '700', marginBottom: 8 },
  price: {
    fontSize: 24,
    fontWeight: '800',
    color: theme.primaryDark,
    marginBottom: 4,
  },
  cat: {
    fontSize: 14,
    color: '#64748b',
    textTransform: 'capitalize',
    marginBottom: 4,
  },
  rating: { fontSize: 14, color: '#334155', marginBottom: 12 },
  desc: { fontSize: 15, lineHeight: 22, color: '#334155', marginBottom: 24 },
  actions: { gap: 12 },
  primary: {
    backgroundColor: theme.primary,
    paddingVertical: 14,
    borderRadius: theme.radiusLg,
    alignItems: 'center',
  },
  pressed: { opacity: 0.88, transform: [{ scale: 0.99 }] },
  primaryText: { color: '#fff', fontWeight: '700', fontSize: 16 },
  secondary: {
    borderWidth: 2,
    borderColor: theme.primary,
    paddingVertical: 12,
    borderRadius: 10,
    alignItems: 'center',
  },
  secondaryOn: { backgroundColor: '#eff6ff' },
  secondaryText: { color: theme.primaryDark, fontWeight: '700', fontSize: 16 },
  secondaryTextOn: { color: theme.primaryDark },
  errorTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 8,
    textAlign: 'center',
  },
  errorBody: { color: '#64748b', textAlign: 'center', marginBottom: 16 },
  retry: {
    backgroundColor: theme.primary,
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 8,
    marginBottom: 12,
  },
  retryText: { color: '#fff', fontWeight: '600' },
  back: { padding: 8 },
  backText: { color: theme.primaryDark, fontWeight: '600' },
  modalBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(15, 23, 42, 0.45)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  modalCard: {
    width: '100%',
    maxWidth: 340,
    backgroundColor: theme.surface,
    borderRadius: theme.radiusLg,
    paddingVertical: 28,
    paddingHorizontal: 22,
    alignItems: 'center',
    ...theme.shadow,
  },
  modalIconCircle: { marginBottom: 12 },
  modalTitle: {
    fontSize: 20,
    fontWeight: '800',
    color: theme.text,
    marginBottom: 8,
    textAlign: 'center',
  },
  modalBody: {
    fontSize: 15,
    lineHeight: 22,
    color: theme.textMuted,
    textAlign: 'center',
    marginBottom: 24,
  },
  modalActions: { width: '100%', gap: 10 },
  modalBtn: {
    paddingVertical: 14,
    borderRadius: theme.radiusLg,
    alignItems: 'center',
  },
  modalBtnGhost: {
    backgroundColor: theme.bg,
    borderWidth: 1,
    borderColor: theme.border,
  },
  modalBtnGhostText: {
    fontSize: 16,
    fontWeight: '700',
    color: theme.text,
  },
  modalBtnSolid: { backgroundColor: theme.primary },
  modalBtnSolidText: { fontSize: 16, fontWeight: '700', color: '#fff' },
});
