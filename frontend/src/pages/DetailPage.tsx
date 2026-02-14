import { Alert, Button, Card, Col, Input, Row, Space, Table, Tag, Typography, message } from "antd";
import { useLocation, useParams } from "react-router-dom";
import { commitProduct, runValidation } from "../api/product";
import { useMemo, useState } from "react";

const { TextArea } = Input;
const { Text } = Typography;

export default function DetailPage() {
  const { productId } = useParams();
  const location = useLocation() as any;
  const accountId = location?.state?.accountId;
  const initial = location?.state?.productData;
  const summary = initial?.view?.summary || {};
  const variants = initial?.view?.variants || [];
  const metafields = initial?.view?.metafields || [];

  const [title, setTitle] = useState(summary.title || "");
  const [vendor, setVendor] = useState(summary.vendor || "");
  const [productType, setProductType] = useState(summary.productType || "");
  const [tags, setTags] = useState((summary.tags || []).join(", "));
  const [validation, setValidation] = useState<any>(null);
  const [validating, setValidating] = useState(false);
  const [committing, setCommitting] = useState(false);

  const updatePayload = useMemo(
    () => ({
      title,
      vendor,
      productType,
      tags,
      productId,
    }),
    [title, vendor, productType, tags, productId]
  );

  const onValidate = async () => {
    try {
      setValidating(true);
      const res = await runValidation(updatePayload);
      if (!res.success) throw new Error(res.message || "Validate failed");
      setValidation(res.data);
      message.success(res.data?.pass ? "Validation passed" : "Validation has issues");
    } catch (e: any) {
      message.error(e.message || "Validate failed");
    } finally {
      setValidating(false);
    }
  };

  const onCommit = async () => {
    try {
      setCommitting(true);
      const res = await commitProduct({
        accountId: Number(accountId),
        productId: String(productId),
        updatePayload,
      });
      if (!res.success) throw new Error(res.message || "Commit failed");
      message.success("Commit request submitted");
    } catch (e: any) {
      message.error(e.message || "Commit failed");
    } finally {
      setCommitting(false);
    }
  };

  return (
    <Space direction="vertical" style={{ width: "100%" }} size={16}>
      <Card title="Detail - Editable Fields">
        <Row gutter={12}>
          <Col span={12}>
            <label>Title</label>
            <Input value={title} onChange={(e) => setTitle(e.target.value)} />
          </Col>
          <Col span={12}>
            <label>Vendor</label>
            <Input value={vendor} onChange={(e) => setVendor(e.target.value)} />
          </Col>
          <Col span={12} style={{ marginTop: 12 }}>
            <label>Product Type</label>
            <Input value={productType} onChange={(e) => setProductType(e.target.value)} />
          </Col>
          <Col span={12} style={{ marginTop: 12 }}>
            <label>Tags (comma separated)</label>
            <Input value={tags} onChange={(e) => setTags(e.target.value)} />
          </Col>
        </Row>

        <div style={{ marginTop: 16 }}>
          <Space>
            <Button onClick={onValidate} loading={validating}>Validate</Button>
            <Button type="primary" onClick={onCommit} loading={committing}>Commit</Button>
          </Space>
        </div>
      </Card>

      <Card title="Validation Result">
        {!validation ? (
          <Text type="secondary">Click Validate to check current edits.</Text>
        ) : validation.pass ? (
          <Alert type="success" message="Validation passed" showIcon />
        ) : (
          <>
            <Alert type="error" message={`Validation failed (${validation.failed} issues)`} showIcon />
            <ul style={{ marginTop: 12 }}>
              {(validation.issues || []).map((it: any, idx: number) => (
                <li key={idx} style={{ color: "red" }}>
                  [{it.fieldPath}] {it.message}
                </li>
              ))}
            </ul>
          </>
        )}
      </Card>

      <Card title="Variants">
        <Table
          size="small"
          rowKey="id"
          dataSource={variants}
          pagination={{ pageSize: 10 }}
          columns={[
            { title: "Title", dataIndex: "title" },
            { title: "SKU", dataIndex: "sku" },
            { title: "Barcode", dataIndex: "barcode" },
            { title: "Price", dataIndex: "price" },
            { title: "Inventory", dataIndex: "inventoryQuantity" },
            {
              title: "Tracked",
              render: (_, r: any) =>
                r?.inventoryItem?.tracked ? <Tag color="green">true</Tag> : <Tag>false</Tag>,
            },
          ]}
        />
      </Card>

      <Card title="Metafields">
        <Table
          size="small"
          rowKey="id"
          dataSource={metafields}
          pagination={{ pageSize: 10 }}
          columns={[
            { title: "Namespace", dataIndex: "namespace" },
            { title: "Key", dataIndex: "key" },
            { title: "Value", dataIndex: "value" },
            { title: "Type", dataIndex: "type" },
          ]}
        />
      </Card>

      <Card title="Description HTML">
        <TextArea rows={6} value={summary.descriptionHtml || ""} readOnly />
      </Card>
    </Space>
  );
}
