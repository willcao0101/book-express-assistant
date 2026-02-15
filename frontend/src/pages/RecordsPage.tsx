import { Button, Card, Input, Space, Table } from "antd";
import { useEffect, useState } from "react";
import { queryRecords } from "../api/records";
import { useNavigate } from "react-router-dom";

/**
 * Convert any product id format to plain numeric id if possible.
 * Examples:
 * - gid://shopify/Product/8112925769802 -> 8112925769802
 * - 8112925769802 -> 8112925769802
 */
function toPlainProductId(raw: any): string {
  if (raw === null || raw === undefined) return "";
  const text = String(raw).trim();
  if (!text) return "";

  // Match trailing numeric segment in gid-like format
  const match = text.match(/\/(\d+)(\?.*)?$/);
  if (match?.[1]) return match[1];

  // Already numeric string
  if (/^\d+$/.test(text)) return text;

  return text;
}

/**
 * Resolve title from possible field shapes.
 */
function resolveTitle(record: any): string {
  return (
    record?.title ??
    record?.productTitle ??
    record?.name ??
    record?.productName ??
    record?.summary?.title ??
    record?.payload?.title ??
    record?.product?.title ??
    "-"
  );
}

/**
 * Resolve list content from multiple backend response shapes.
 */
function resolveContent(res: any): any[] {
  return (
    res?.data?.content ??
    res?.data?.data?.content ??
    res?.data?.records ??
    res?.records ??
    []
  );
}

export default function RecordsPage() {
  const navigate = useNavigate();

  const [loading, setLoading] = useState(false);
  const [records, setRecords] = useState<any[]>([]);

  const [page, setPage] = useState(0); // 0-based for backend
  const [size, setSize] = useState(20);

  const [recordId, setRecordId] = useState<string>("");
  const [title, setTitle] = useState<string>("");

  const load = async (p = page, s = size) => {
    setLoading(true);
    try {
      const res = await queryRecords({
        page: p,
        size: s,
        id: recordId ? Number(recordId) : undefined,
        title: title ? title.trim() : undefined,
      });

      const content = resolveContent(res);
      setRecords(content);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(0, 20);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <Space direction="vertical" style={{ width: "100%" }} size={16}>
      <Card title="Records Query">
        <Space wrap>
          <Input
            placeholder="Record ID"
            value={recordId}
            onChange={(e) => setRecordId(e.target.value)}
            style={{ width: 180 }}
          />
          <Input
            placeholder="Product Title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            style={{ width: 260 }}
          />
          <Button
            type="primary"
            onClick={() => {
              setPage(0);
              load(0, size);
            }}
          >
            Search
          </Button>
          <Button
            onClick={() => {
              setRecordId("");
              setTitle("");
              setPage(0);
              load(0, size);
            }}
          >
            Reset
          </Button>
        </Space>
      </Card>

      <Card title="Local Product Records">
        <Table
          loading={loading}
          rowKey={(r) => String(r?.id ?? `${r?.productId ?? "row"}-${r?.createdAt ?? Math.random()}`)}
          dataSource={records}
          pagination={{
            current: page + 1, // UI is 1-based
            pageSize: size,
            onChange: (p, ps) => {
              const nextPage = p - 1;
              setPage(nextPage);
              setSize(ps);
              load(nextPage, ps);
            },
          }}
          columns={[
            {
              title: "ID",
              dataIndex: "id",
              width: 90,
              render: (v: any) => (v ?? "-"),
            },
            {
              title: "Product ID",
              dataIndex: "productId",
              width: 180,
              render: (v: any) => toPlainProductId(v) || "-",
            },
            {
              title: "Title",
              width: 280,
              render: (_: any, r: any) => resolveTitle(r),
            },
            {
              title: "Status",
              dataIndex: "status",
              width: 120,
              render: (v: any) => (v ?? "-"),
            },
            {
              title: "Message",
              dataIndex: "message",
              render: (v: any) => (v ?? "-"),
            },
            {
              title: "Created At",
              dataIndex: "createdAt",
              width: 200,
              render: (v: any) => (v ?? "-"),
            },
            {
              title: "Action",
              width: 120,
              render: (_: any, r: any) => (
                <Button
                  size="small"
                  onClick={() =>
                    navigate(`/detail/${encodeURIComponent(toPlainProductId(r?.productId))}`, {
                      state: {
                        productId: toPlainProductId(r?.productId),
                        record: r,
                      },
                    })
                  }
                >
                  Detail
                </Button>
              ),
            },
          ]}
        />
      </Card>
    </Space>
  );
}
