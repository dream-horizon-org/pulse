"use server";

export interface CartResult {
  ok: boolean;
  productId: string;
  productName: string;
  error?: string;
}

/** Server Action — simulates adding an item to a cart (DB write). */
export async function addToCartAction(
  productId: string,
  productName: string,
): Promise<CartResult> {
  await new Promise((r) => setTimeout(r, 120)); // simulate DB write

  // Simulate occasional failure for error-tracking demo
  if (productId === "p3") {
    return { ok: false, productId, productName, error: "Item out of stock" };
  }

  return { ok: true, productId, productName };
}
