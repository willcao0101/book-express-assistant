import client from "./client";

export async function fetchProductById(payload: { accountId: number; productId: string }) {
  const res = await client.post("/api/v1/shopify/fetch-by-product-id", payload);
  return res.data;
}

export async function runValidation(productData: Record<string, unknown>) {
  const res = await client.post("/api/v1/validation/run", { productData });
  return res.data;
}

export async function commitProduct(payload: {
  accountId: number;
  productId: string;
  updatePayload: Record<string, unknown>;
}) {
  const res = await client.post("/api/v1/commit", payload);
  return res.data;
}
