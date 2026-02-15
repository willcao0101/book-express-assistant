import client from "./client";

export type ApiResponse<T = any> = {
  success: boolean;
  message?: string;
  data: T;
};

export type FetchProductPayload = {
  accountId: number;
  productId: string; // numeric id or gid string
};

export type ProductUpdatePayload = {
  productId?: string;
  title?: string;
  vendor?: string;
  productType?: string;
  tags?: string[] | string;
  descriptionHtml?: string;
  metafields?: Array<{
    namespace?: string;
    key?: string;
    type?: string;
    value?: string;
  }>;
  [key: string]: any;
};

export type CommitPayload = {
  accountId: number;
  productId: string;
  updatePayload: ProductUpdatePayload;
};

/**
 * Fetch product detail by product id and account id.
 */
export async function fetchProductById(payload: FetchProductPayload): Promise<ApiResponse<any>> {
  const res = await client.post("/api/v1/shopify/fetch-by-product-id", payload);
  return res.data;
}

/**
 * Run validation based on current edited product payload.
 * Backend contract expects: { productData: {...} }
 */
export async function runValidation(productData: ProductUpdatePayload): Promise<ApiResponse<any>> {
  const res = await client.post("/api/v1/validation/run", { productData });
  return res.data;
}

/**
 * Commit edited product data.
 */
export async function commitProduct(payload: CommitPayload): Promise<ApiResponse<any>> {
  const res = await client.post("/api/v1/commit", payload);
  return res.data;
}
