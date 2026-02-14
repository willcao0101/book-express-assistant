import client from "./client";

export async function listAccounts() {
  const res = await client.get("/api/v1/accounts");
  return res.data;
}

export async function createAccount(payload: any) {
  const res = await client.post("/api/v1/accounts", payload);
  return res.data;
}

export async function updateAccount(id: number, payload: any) {
  const res = await client.put(`/api/v1/accounts/${id}`, payload);
  return res.data;
}

export async function oauthStart(payload: { accountId: number; redirectUri?: string }) {
  const res = await client.post("/api/v1/oauth/start", payload);
  return res.data;
}

export async function oauthCallback(payload: { accountId: number; code: string; state?: string }) {
  const res = await client.post("/api/v1/oauth/callback", payload);
  return res.data;
}
