import type { Product } from './types';

/** Public HTTPS API — verified reachable for products, categories, and auth. */
const BASE = 'https://dummyjson.com';

export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function fetchJson<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    ...init,
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  });
  const text = await res.text();
  let body: unknown = null;
  if (text) {
    try {
      body = JSON.parse(text) as unknown;
    } catch {
      body = text;
    }
  }
  if (!res.ok) {
    const msg =
      typeof body === 'object' && body !== null && 'message' in body
        ? String((body as { message: unknown }).message)
        : res.statusText;
    throw new ApiError(msg || `HTTP ${res.status}`, res.status);
  }
  return body as T;
}

export type ProductCategory = {
  slug: string;
  name: string;
  url: string;
  /**
   * DummyJSON has no category image field; we attach the first product thumbnail
   * per slug from `GET /products` so the list still shows real CDN images.
   */
  image?: string;
};

export async function getProductCategories(): Promise<ProductCategory[]> {
  const [categories, catalog] = await Promise.all([
    fetchJson<ProductCategory[]>('/products/categories'),
    fetchJson<DummyProductsResponse>('/products?limit=200').catch(() => null),
  ]);
  if (!catalog?.products?.length) {
    return categories;
  }
  const imageBySlug = new Map<string, string>();
  for (const p of catalog.products) {
    const img = p.thumbnail || p.images?.[0];
    if (!img) continue;
    if (!imageBySlug.has(p.category)) {
      imageBySlug.set(p.category, img);
    }
  }
  return categories.map((c) => ({
    ...c,
    image: imageBySlug.get(c.slug),
  }));
}

type DummyJsonProduct = {
  id: number;
  title: string;
  description: string;
  category: string;
  price: number;
  rating: number;
  thumbnail?: string;
  images?: string[];
  reviews?: { rating: number }[];
};

type DummyProductsResponse = {
  products: DummyJsonProduct[];
  total: number;
  skip: number;
  limit: number;
};

function mapProduct(p: DummyJsonProduct): Product {
  const image = p.thumbnail || p.images?.[0] || '';
  return {
    id: p.id,
    title: p.title,
    price: p.price,
    description: p.description,
    category: p.category,
    image,
    rating: {
      rate: p.rating,
      count: p.reviews?.length ?? 0,
    },
  };
}

export async function getProducts(limit = 100): Promise<Product[]> {
  const res = await fetchJson<DummyProductsResponse>(
    `/products?limit=${Math.min(limit, 250)}`
  );
  return res.products.map(mapProduct);
}

export async function getProductsByCategory(
  categorySlug: string
): Promise<Product[]> {
  const enc = encodeURIComponent(categorySlug);
  const res = await fetchJson<DummyProductsResponse>(
    `/products/category/${enc}?limit=100`
  );
  return res.products.map(mapProduct);
}

export async function getProduct(id: number): Promise<Product> {
  const raw = await fetchJson<DummyJsonProduct>(`/products/${id}`);
  return mapProduct(raw);
}

export type LoginResponse = { token: string };

type DummyLoginResponse = {
  accessToken: string;
  refreshToken: string;
  id: number;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  gender: string;
  image: string;
};

export async function login(
  username: string,
  password: string
): Promise<LoginResponse> {
  const r = await fetchJson<DummyLoginResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password, expiresInMins: 60 }),
  });
  return { token: r.accessToken };
}

export type RegisterUserPayload = {
  email: string;
  username: string;
  password: string;
  firstname: string;
  lastname: string;
  phone?: string;
};

export async function registerUser(
  payload: RegisterUserPayload
): Promise<{ id: number }> {
  const r = await fetchJson<{ id: number }>('/users/add', {
    method: 'POST',
    body: JSON.stringify({
      firstName: payload.firstname,
      lastName: payload.lastname,
      username: payload.username,
      password: payload.password,
      email: payload.email,
      age: 21,
    }),
  });
  return { id: r.id };
}
