import client from "./client";

export async function queryRecords(params: {
  page?: number;
  size?: number;
  id?: number;
  title?: string;
}) {
  const res = await client.get("/api/v1/records", { params });
  return res.data;
}
