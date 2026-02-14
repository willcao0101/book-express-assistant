import { Button, Card, Col, Form, Input, Row, Space, Typography, message } from "antd";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchProductById } from "../api/product";
import { listAccounts } from "../api/settings";

const { Text } = Typography;

export default function ProductPage() {
  const [form] = Form.useForm();
  const navigate = useNavigate();

  const [defaultAccountId, setDefaultAccountId] = useState<number | null>(null);
  const [result, setResult] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const res = await listAccounts();
        const data = res?.data || [];
        if (!data.length) {
          message.error("No account configured. Please go to Settings first.");
          return;
        }
        const def = data.find((a: any) => a.isDefault) || data[0];
        setDefaultAccountId(def.id);
      } catch (e: any) {
        message.error(e.message || "Failed to load default account");
      }
    })();
  }, []);

  const onSearch = async () => {
    try {
      if (!defaultAccountId) {
        message.error("Default account is not available. Please configure Settings.");
        return;
      }

      const values = await form.validateFields();
      setLoading(true);

      const res = await fetchProductById({
        accountId: defaultAccountId,
        productId: values.productId.trim(),
      });

      if (!res.success) throw new Error(res.message || "Fetch failed");
      setResult(res.data);
      message.success("Product fetched");
    } catch (err: any) {
      message.error(err.message || "Fetch failed");
    } finally {
      setLoading(false);
    }
  };

  const summary = result?.view?.summary;

  return (
    <Space direction="vertical" style={{ width: "100%" }} size={16}>
      <Card title="Product Query">
        <Form form={form} layout="vertical">
          <Row gutter={12}>
            <Col xs={24} md={20}>
              <Form.Item
                name="productId"
                label="Product ID (GID)"
                // initialValue="8112925769802"
                rules={[{ required: true, message: "Please input product GID" }]}
              >
                <Input placeholder="8112925769802" />
              </Form.Item>
            </Col>
            <Col xs={24} md={4} style={{ display: "flex", alignItems: "end" }}>
              <Form.Item style={{ width: "100%" }}>
                <Button block type="primary" onClick={onSearch} loading={loading}>
                  Search
                </Button>
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Card>

      <Card title="Basic Information">
        {!summary ? (
          <Text type="secondary">No data loaded yet.</Text>
        ) : (
          <>
            <p><b>Title:</b> {summary.title}</p>
            <p><b>Vendor:</b> {summary.vendor}</p>
            <p><b>Status:</b> {summary.status}</p>
            <p><b>Product Type:</b> {summary.productType || "-"}</p>
            <p><b>Tags:</b> {(summary.tags || []).join(", ") || "-"}</p>
            <Button
              type="primary"
              onClick={() =>
                navigate(`/detail/${encodeURIComponent(summary.id)}`, {
                  state: {
                    accountId: defaultAccountId,
                    productData: result,
                  },
                })
              }
            >
              Go to Detail
            </Button>
          </>
        )}
      </Card>
    </Space>
  );
}
