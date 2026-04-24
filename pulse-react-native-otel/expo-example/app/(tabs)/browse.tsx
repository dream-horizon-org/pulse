import { useCallback, useEffect, useMemo, useState } from 'react';
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
import { useRouter } from 'expo-router';
import { ProductImage } from '../../components/ProductImage';
import { ApiError, getProducts } from '../../shared/api';
import { labelFromCategorySlug } from '../../shared/formatCategory';
import type { Product, SortKey } from '../../shared/types';

function sortProducts(list: Product[], sort: SortKey): Product[] {
  const copy = [...list];
  if (sort === 'price-asc') copy.sort((a, b) => a.price - b.price);
  else if (sort === 'price-desc') copy.sort((a, b) => b.price - a.price);
  else if (sort === 'title')
    copy.sort((a, b) => a.title.localeCompare(b.title));
  return copy;
}

export default function BrowseScreen() {
  const router = useRouter();
  const [raw, setRaw] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState<SortKey>('default');
  const [categoryFilter, setCategoryFilter] = useState<string>('');

  const load = useCallback(async () => {
    setError(null);
    try {
      const list = await getProducts();
      setRaw(list);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const onRefresh = useCallback(() => {
    setRefreshing(true);
    void load();
  }, [load]);

  const categories = useMemo(() => {
    const s = new Set(raw.map((p) => p.category));
    return [...s].sort();
  }, [raw]);

  const filtered = useMemo(() => {
    let list = raw;
    const q = query.trim().toLowerCase();
    if (q) list = list.filter((p) => p.title.toLowerCase().includes(q));
    if (categoryFilter)
      list = list.filter((p) => p.category === categoryFilter);
    return sortProducts(list, sort);
  }, [raw, query, sort, categoryFilter]);

  if (loading && raw.length === 0) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  if (error && raw.length === 0) {
    return (
      <View style={[styles.center, { paddingHorizontal: 24 }]}>
        <Text style={styles.errorTitle}>Could not load catalog</Text>
        <Text style={styles.errorBody}>{error}</Text>
        <Pressable style={styles.retry} onPress={() => void load()}>
          <Text style={styles.retryText}>Retry</Text>
        </Pressable>
      </View>
    );
  }

  return (
    <View style={styles.flex} testID="browse-tab">
      <Text style={styles.heading}>All products</Text>
      <TextInput
        style={styles.input}
        placeholder="Search catalog…"
        value={query}
        onChangeText={setQuery}
        autoCapitalize="none"
        autoCorrect={false}
      />
      <FlatList
        horizontal
        data={[
          { id: 'all', label: 'All' },
          ...categories.map((c) => ({
            id: c,
            label: labelFromCategorySlug(c),
          })),
        ]}
        keyExtractor={(item) => item.id}
        style={styles.catStrip}
        contentContainerStyle={{ paddingHorizontal: 12, gap: 8 }}
        renderItem={({ item }) => {
          const selected =
            item.id === 'all'
              ? categoryFilter === ''
              : categoryFilter === item.id;
          return (
            <Pressable
              style={[styles.catChip, selected && styles.catChipOn]}
              onPress={() =>
                setCategoryFilter(item.id === 'all' ? '' : item.id)
              }
            >
              <Text
                numberOfLines={1}
                style={[styles.catChipText, selected && styles.catChipTextOn]}
              >
                {item.label}
              </Text>
            </Pressable>
          );
        }}
        showsHorizontalScrollIndicator={false}
      />
      <View style={styles.row}>
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
      <FlatList
        data={filtered}
        keyExtractor={(item) => String(item.id)}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }
        renderItem={({ item, index }) => (
          <Pressable
            style={styles.card}
            testID={`product-${index}`}
            onPress={() => router.push(`/product/${item.id}`)}
          >
            <ProductImage uri={item.image} style={styles.thumb} />
            <View style={styles.cardBody}>
              <Text numberOfLines={2} style={styles.cardTitle}>
                {item.title}
              </Text>
              <Text style={styles.meta}>
                {labelFromCategorySlug(item.category)}
              </Text>
              <Text style={styles.price}>${item.price.toFixed(2)}</Text>
            </View>
          </Pressable>
        )}
        ItemSeparatorComponent={() => <View style={{ height: 10 }} />}
        contentContainerStyle={{ padding: 12, paddingBottom: 24 }}
        ListEmptyComponent={
          <Text style={styles.empty}>No products match your filters.</Text>
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: '#fff' },
  center: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#fff',
  },
  heading: {
    fontSize: 22,
    fontWeight: '700',
    paddingHorizontal: 12,
    marginBottom: 8,
  },
  input: {
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    marginHorizontal: 12,
    marginBottom: 8,
    fontSize: 16,
  },
  catStrip: { maxHeight: 44, marginBottom: 8 },
  catChip: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: '#f1f5f9',
    maxWidth: 160,
  },
  catChipOn: { backgroundColor: '#2563eb' },
  catChipText: { fontSize: 13, color: '#334155', textTransform: 'capitalize' },
  catChipTextOn: { color: '#fff', fontWeight: '600' },
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    paddingHorizontal: 12,
    marginBottom: 8,
  },
  chip: {
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: '#f1f5f9',
  },
  chipOn: { backgroundColor: '#2563eb' },
  chipText: { fontSize: 12, color: '#334155' },
  chipTextOn: { color: '#fff', fontWeight: '600' },
  card: {
    flexDirection: 'row',
    backgroundColor: '#f8fafc',
    borderRadius: 12,
    overflow: 'hidden',
    padding: 10,
    gap: 12,
  },
  thumb: { width: 72, height: 72, borderRadius: 8, backgroundColor: '#e2e8f0' },
  cardBody: { flex: 1, justifyContent: 'center' },
  cardTitle: { fontSize: 15, fontWeight: '600', marginBottom: 2 },
  meta: {
    fontSize: 12,
    color: '#64748b',
    textTransform: 'capitalize',
    marginBottom: 4,
  },
  price: { fontSize: 16, fontWeight: '700', color: '#2563eb' },
  empty: { textAlign: 'center', color: '#64748b', marginTop: 24 },
  errorTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 8,
    textAlign: 'center',
  },
  errorBody: { color: '#64748b', textAlign: 'center', marginBottom: 16 },
  retry: {
    backgroundColor: '#2563eb',
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 8,
  },
  retryText: { color: '#fff', fontWeight: '600' },
});
