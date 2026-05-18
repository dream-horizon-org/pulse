export interface Product {
  id: string;
  name: string;
  price: number;
  category: string;
  description: string;
  inStock: boolean;
}

export const PRODUCTS: Product[] = [
  {
    id: "p1",
    name: "Trail Runner X",
    price: 129,
    category: "footwear",
    description: "Lightweight trail running shoe with aggressive outsole.",
    inStock: true,
  },
  {
    id: "p2",
    name: "Carbon Fibre Racket",
    price: 249,
    category: "equipment",
    description: "Professional-grade racket for competitive play.",
    inStock: true,
  },
  {
    id: "p3",
    name: "Pro Cycling Helmet",
    price: 89,
    category: "safety",
    description: "MIPS-certified helmet with ventilation channels.",
    inStock: false,
  },
  {
    id: "p4",
    name: "Compression Shorts",
    price: 45,
    category: "apparel",
    description: "4-way stretch fabric with moisture-wicking lining.",
    inStock: true,
  },
  {
    id: "p5",
    name: "GPS Sports Watch",
    price: 349,
    category: "tech",
    description: "Heart-rate monitor, GPS tracking, 7-day battery life.",
    inStock: true,
  },
  {
    id: "p6",
    name: "Foam Roller Pro",
    price: 39,
    category: "recovery",
    description: "High-density EVA foam for deep tissue recovery.",
    inStock: true,
  },
];

/** Simulates async DB/API fetch — runs on the server in RSC. */
export async function getProducts(): Promise<Product[]> {
  await new Promise((r) => setTimeout(r, 50)); // simulate latency
  return PRODUCTS;
}

export async function getProduct(id: string): Promise<Product | null> {
  await new Promise((r) => setTimeout(r, 30));
  return PRODUCTS.find((p) => p.id === id) ?? null;
}

export async function getFeaturedProducts(): Promise<Product[]> {
  await new Promise((r) => setTimeout(r, 40));
  return PRODUCTS.filter((p) => p.inStock).slice(0, 3);
}
