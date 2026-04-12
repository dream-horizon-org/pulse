import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useState,
} from 'react';
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  RefreshControl,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useNavigation, useLocalSearchParams, useRouter } from 'expo-router';
import { ProductImage } from '../../components/ProductImage';
import { ApiError, getProductsByCategory } from '../../lib/api';
import { labelFromCategorySlug } from '../../lib/formatCategory';
import { theme } from '../../lib/theme';
import type { Product, SortKey } from '../../lib/types';

function sortProducts(list: Product[], sort: SortKey): Product[] {
  const copy = [...list];
  if (sort === 'price-asc') copy.sort((a, b) => a.price - b.price);
  else if (sort === 'price-desc') copy.sort((a, b) => b.price - a.price);
  else if (sort === 'title')
    copy.sort((a, b) => a.title.localeCompare(b.title));
  return copy;
}

export default function CategoryScreen() {
  const { slug: slugParam } = useLocalSearchParams<{ slug: string }>();
  const categorySlug = slugParam ? decodeURIComponent(slugParam) : '';
  const navigation = useNavigation();
  const router = useRouter();
  const [raw, setRaw] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState<SortKey>('default');
  const [minPrice, setMinPrice] = useState('');
  const [maxPrice, setMaxPrice] = useState('');

  useLayoutEffect(() => {
    navigation.setOptions({
      title: categorySlug ? labelFromCategorySlug(categorySlug) : 'Category',
    });
  }, [navigation, categorySlug]);

  const load = useCallback(async () => {
    if (!categorySlug) return;
    setError(null);
    try {
      const list = await getProductsByCategory(categorySlug);
      setRaw(list);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [categorySlug]);

  useEffect(() => {
    setLoading(true);
    void load();
  }, [load]);

  const onRefresh = useCallback(() => {
    setRefreshing(true);
    void load();
  }, [load]);

  const filtered = useMemo(() => {
    let list = raw;
    const q = query.trim().toLowerCase();
    if (q) {
      list = list.filter((p) => p.title.toLowerCase().includes(q));
    }
    const min = minPrice === '' ? null : Number(minPrice);
    const max = maxPrice === '' ? null : Number(maxPrice);
    if (min !== null && !Number.isNaN(min))
      list = list.filter((p) => p.price >= min);
    if (max !== null && !Number.isNaN(max))
      list = list.filter((p) => p.price <= max);
    return sortProducts(list, sort);
  }, [raw, query, sort, minPrice, maxPrice]);

  if (loading && raw.length === 0) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color={theme.primary} />
      </View>
    );
  }

  if (error && raw.length === 0) {
    return (
      <View style={[styles.center, { paddingHorizontal: 24 }]}>
        <Text style={styles.errorTitle}>Could not load products</Text>
        <Text style={styles.errorBody}>{error}</Text>
        <Pressable style={styles.retry} onPress={() => void load()}>
          <Text style={styles.retryText}>Retry</Text>
        </Pressable>
      </View>
    );
  }

  return (
    <View style={styles.flex}>
      <TextInput
        style={styles.input}
        placeholder="Search in category…"
        placeholderTextColor={theme.textMuted}
        value={query}
        onChangeText={setQuery}
        autoCapitalize="none"
        autoCorrect={false}
      />
      <View style={styles.row}>
        <Text style={styles.label}>Sort</Text>
        {(['default', 'price-asc', 'price-desc', 'title'] as SortKey[]).map(
          (k) => (
            <Pressable
              key={k}
              style={[styles.chip, sort === k && styles.chipOn]}
              onPress={() => setSort(k)}
            >
              <Text style={[styles.chipText, sort === k && styles.chipTextOn]}>
                {k === 'default'
                  ? 'Default'
                  : k === 'price-asc'
                    ? 'Price ↑'
                    : k === 'price-desc'
                      ? 'Price ↓'
                      : 'Title'}
              </Text>
            </Pressable>
          )
        )}
      </View>
      <View style={styles.priceRow}>
        <TextInput
          style={[styles.input, styles.priceInput]}
          placeholder="Min"
          placeholderTextColor={theme.textMuted}
          keyboardType="decimal-pad"
          value={minPrice}
          onChangeText={setMinPrice}
        />
        <TextInput
          style={[styles.input, styles.priceInput]}
          placeholder="Max"
          placeholderTextColor={theme.textMuted}
          keyboardType="decimal-pad"
          value={maxPrice}
          onChangeText={setMaxPrice}
        />
      </View>
      <FlatList
        data={filtered}
        keyExtractor={(item) => String(item.id)}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor={theme.primary}
          />
        }
        renderItem={({ item }) => (
          <Pressable
            style={styles.card}
            onPress={() => router.push(`/product/${item.id}`)}
          >
            <ProductImage uri={item.image} style={styles.thumb} />
            <View style={styles.cardBody}>
              <Text numberOfLines={2} style={styles.cardTitle}>
                {item.title}
              </Text>
              <Text style={styles.price}>${item.price.toFixed(2)}</Text>
            </View>
          </Pressable>
        )}
        ItemSeparatorComponent={() => <View style={{ height: 12 }} />}
        contentContainerStyle={{ padding: 14, paddingBottom: 28 }}
        ListEmptyComponent={
          <Text style={styles.empty}>No products match your filters.</Text>
        }
      />
    </View>
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
  input: {
    borderWidth: 1,
    borderColor: theme.border,
    borderRadius: theme.radiusMd,
    paddingHorizontal: 14,
    paddingVertical: 11,
    marginHorizontal: 14,
    marginTop: 10,
    marginBottom: 8,
    fontSize: 16,
    backgroundColor: theme.surface,
    color: theme.text,
  },
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    paddingHorizontal: 14,
    marginBottom: 8,
    alignItems: 'center',
  },
  label: { marginRight: 4, color: theme.textMuted, fontSize: 13 },
  chip: {
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 20,
    backgroundColor: theme.surface,
    borderWidth: 1,
    borderColor: theme.border,
  },
  chipOn: { backgroundColor: theme.primary, borderColor: theme.primary },
  chipText: { fontSize: 12, color: theme.text, fontWeight: '600' },
  chipTextOn: { color: '#fff' },
  priceRow: {
    flexDirection: 'row',
    gap: 10,
    paddingHorizontal: 14,
    marginBottom: 8,
  },
  priceInput: { flex: 1, marginHorizontal: 0, marginTop: 0 },
  card: {
    flexDirection: 'row',
    backgroundColor: theme.surface,
    borderRadius: theme.radiusLg,
    padding: 12,
    gap: 14,
    borderWidth: 1,
    borderColor: theme.border,
    ...theme.shadow,
  },
  thumb: {
    width: 76,
    height: 76,
    borderRadius: theme.radiusMd,
    backgroundColor: theme.border,
  },
  cardBody: { flex: 1, justifyContent: 'center' },
  cardTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: theme.text,
    marginBottom: 6,
  },
  price: { fontSize: 17, fontWeight: '800', color: theme.primaryDark },
  empty: { textAlign: 'center', color: theme.textMuted, marginTop: 28 },
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
