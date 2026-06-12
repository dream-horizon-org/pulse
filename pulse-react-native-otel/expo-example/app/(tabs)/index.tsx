import { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useRouter } from 'expo-router';
import { ProductImage } from '../../components/ProductImage';
import { useShop } from '../../context/ShopContext';
import {
  ApiError,
  getProduct,
  getProductCategories,
  type ProductCategory,
} from '../../shared/api';
import { theme } from '../../shared/theme';
import type { Product } from '../../shared/types';

export default function HomeScreen() {
  const router = useRouter();
  const { recentProductIds } = useShop();
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [recent, setRecent] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const list = await getProductCategories();
      setCategories(list);
    } catch (e) {
      setCategories([]);
      setError(
        e instanceof ApiError
          ? `${e.message} (HTTP ${e.status})`
          : 'Something went wrong'
      );
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    let cancelled = false;
    const ids = recentProductIds.slice(0, 8);
    if (ids.length === 0) {
      setRecent([]);
      return;
    }
    void (async () => {
      try {
        const products = await Promise.all(ids.map((pid) => getProduct(pid)));
        if (!cancelled) setRecent(products);
      } catch {
        if (!cancelled) setRecent([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [recentProductIds]);

  const onRefresh = useCallback(() => {
    setRefreshing(true);
    void load();
  }, [load]);

  if (loading && categories.length === 0) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color={theme.primary} />
      </View>
    );
  }

  if (error && categories.length === 0) {
    return (
      <View style={[styles.center, { paddingHorizontal: 24 }]}>
        <Text style={styles.errorTitle}>Could not load categories</Text>
        <Text style={styles.errorBody}>{error}</Text>
        <Pressable style={styles.retry} onPress={() => void load()}>
          <Text style={styles.retryText}>Retry</Text>
        </Pressable>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <FlatList
        data={categories}
        keyExtractor={(item) => item.slug}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor={theme.primary}
          />
        }
        ListHeaderComponent={
          <>
            {recent.length > 0 ? (
              <View style={styles.recentBlock}>
                <Text style={styles.subheading}>Recently viewed</Text>
                <ScrollView
                  horizontal
                  showsHorizontalScrollIndicator={false}
                  contentContainerStyle={styles.recentStrip}
                >
                  {recent.map((p) => (
                    <Pressable
                      key={p.id}
                      style={styles.recentCard}
                      onPress={() => router.push(`/product/${p.id}`)}
                    >
                      <ProductImage uri={p.image} style={styles.recentThumb} />
                      <Text numberOfLines={2} style={styles.recentTitle}>
                        {p.title}
                      </Text>
                    </Pressable>
                  ))}
                </ScrollView>
              </View>
            ) : null}
            <Text style={styles.heading}>Browse by category</Text>
          </>
        }
        renderItem={({ item }) => (
          <Pressable
            style={styles.row}
            onPress={() =>
              router.push(`/category/${encodeURIComponent(item.slug)}`)
            }
          >
            <ProductImage uri={item.image ?? ''} style={styles.catThumb} />
            <Text style={styles.rowText}>{item.name}</Text>
            <Text style={styles.chevron}>›</Text>
          </Pressable>
        )}
        ItemSeparatorComponent={() => <View style={styles.sep} />}
        ListEmptyComponent={<Text style={styles.empty}>No categories</Text>}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: theme.bg },
  center: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: theme.bg,
  },
  recentBlock: { marginBottom: 4, paddingTop: 4 },
  subheading: {
    fontSize: 15,
    fontWeight: '800',
    paddingHorizontal: 16,
    marginBottom: 10,
    color: theme.text,
    letterSpacing: 0.2,
  },
  recentStrip: { paddingHorizontal: 12, gap: 12 },
  recentCard: { width: 124 },
  recentThumb: {
    width: 124,
    height: 124,
    borderRadius: theme.radiusLg,
    backgroundColor: theme.border,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: theme.border,
  },
  recentTitle: { fontSize: 12, color: theme.text, fontWeight: '600' },
  heading: {
    fontSize: 15,
    fontWeight: '800',
    paddingHorizontal: 16,
    paddingTop: 14,
    paddingBottom: 10,
    color: theme.text,
    letterSpacing: 0.3,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    paddingVertical: 12,
    paddingHorizontal: 16,
    marginHorizontal: 12,
    marginBottom: 8,
    backgroundColor: theme.surface,
    borderRadius: theme.radiusLg,
    borderWidth: 1,
    borderColor: theme.border,
    ...theme.shadow,
  },
  catThumb: {
    width: 54,
    height: 54,
    borderRadius: theme.radiusMd,
    backgroundColor: theme.border,
  },
  rowText: { fontSize: 16, flex: 1, fontWeight: '700', color: theme.text },
  chevron: { fontSize: 22, color: theme.textMuted },
  sep: { height: 4 },
  empty: { textAlign: 'center', marginTop: 24, color: theme.textMuted },
  errorTitle: {
    fontSize: 18,
    fontWeight: '700',
    marginBottom: 8,
    textAlign: 'center',
    color: theme.text,
  },
  errorBody: { color: theme.textMuted, textAlign: 'center', marginBottom: 16 },
  retry: {
    backgroundColor: theme.primary,
    paddingHorizontal: 22,
    paddingVertical: 11,
    borderRadius: theme.radiusMd,
  },
  retryText: { color: '#fff', fontWeight: '700' },
});
