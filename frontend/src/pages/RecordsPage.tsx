import { Button, Card, Input, Space, Table } from "antd";
import { useEffect, useState } from "react";
import { queryRecords } from "../api/records";
import { useNavigate } from "react-router-dom";

export default function RecordsPage() {
  const navigate = useNavigate();

  const [loading, setLoading] = useState(false);
  const [records, setRecords] = useState<any[]>([]);

  const [page, setPage] = useState(0);
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

      // Compatible with Spring Page response structure
      setRecords(res?.data?.content || []);
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
          <Button onClick={() => load(0, size)} type="primary">
            Search
          </Button>
          <Button
            onClick={() => {
              setRecordId("");
              setTitle("");
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
          rowKey="id"
          dataSource={records}
          pagination={{
            pageSize: size,
            onChange: (p, ps) => {
              const np = p - 1;
              setPage(np);
              setSize(ps);
              load(np, ps);
            },
          }}
          columns={[
            { title: "ID", dataIndex: "id", width: 90 },
            { title: "Product ID", dataIndex: "productId" },
            { title: "Title", dataIndex: "title", width: 240 },
            { title: "Status", dataIndex: "status", width: 120 },
            { title: "Message", dataIndex: "message" },
            { title: "Created At", dataIndex: "createdAt", width: 200 },
            {
              title: "Action",
              width: 120,
              render: (_: any, r: any) => (
                <Button
                  size="small"
                  onClick={() => navigate(`/detail/${encodeURIComponent(r.productId)}`)}
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
