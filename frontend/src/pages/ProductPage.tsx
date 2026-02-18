import { Button, Card, Col, Form, Input, Row, Space, Typography, message } from "antd";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchProductById } from "../api/product";
import { listAccounts } from "../api/settings";

const { Text } = Typography;
const PRODUCT_SEARCH_CACHE_KEY = "bea_product_search_form";

export default function ProductPage() {
  const [form] = Form.useForm();
  const navigate = useNavigate();

  const [defaultAccountId, setDefaultAccountId] = useState<number | null>(null);
  const [result, setResult] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const cached = sessionStorage.getItem(PRODUCT_SEARCH_CACHE_KEY);
    if (cached) {
      try {
        const parsed = JSON.parse(cached);
        form.setFieldsValue({ productId: parsed.productId || "" });
        setResult(parsed.result || null);
      } catch {
        // ignore malformed cache
      }
    }

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
  }, [form]);

  const onSearch = async () => {
    try {
      if (!defaultAccountId) {
        message.error("Default account is not available. Please configure Settings.");
        return;
      }

      const values = await form.validateFields();
      setLoading(true);

      const inputId = String(values.productId).trim();
      const res = await fetchProductById({
        accountId: defaultAccountId,
        productId: inputId,
      });

      if (!res.success) throw new Error(res.message || "Fetch failed");
      setResult(res.data);
      sessionStorage.setItem(
        PRODUCT_SEARCH_CACHE_KEY,
        JSON.stringify({ productId: inputId, result: res.data })
      );
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
                label="Product ID"
                rules={[{ required: true, message: "Please input product ID" }]}
              >
                <Input placeholder="e.g. 8112925769802" />
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
            <p><b>Tags Title:</b> {summary.tagsTitle || "-"}</p>
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
