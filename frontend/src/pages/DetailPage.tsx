import { Alert, Button, Card, Input, Space, Table, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useLocation, useParams } from "react-router-dom";
import { useEffect, useMemo, useState } from "react";
import { commitProduct, fetchProductById, runValidation } from "../api/product";
import { listAccounts } from "../api/settings";

const { Text } = Typography;

// Shared geometry for strict alignment
const COL_1 = 220;
const COL_2 = 220;
const COL_3 = 220;
const COL_4 = 260;
const COL_5 = 360;
const TABLE_X = COL_1 + COL_2 + COL_3 + COL_4 + COL_5; // 1280

type EditableSummaryField = "title" | "vendor" | "productType" | "tags" | "descriptionHtml";

type SummaryData = {
  id?: string;
  title?: string;
  handle?: string;
  status?: string;
  vendor?: string;
  productType?: string;
  tags?: string[] | string;
  descriptionHtml?: string;
  createdAt?: string;
  updatedAt?: string;
};

type MetafieldItem = {
  id?: string;
  namespace?: string;
  key?: string;
  type?: string;
  value?: string;
};

type SummaryRow = {
  rowKey: string;
  field: string;
  value: string;
  editable: boolean;
  error: string;
  bindField?: EditableSummaryField | "";
};

type MetafieldRow = {
  rowKey: string;
  index: number;
  namespace: string;
  key: string;
  type: string;
  value: string;
  error: string;
};

function toStringValue(v: any): string {
  if (v === null || v === undefined) return "";
  if (Array.isArray(v)) return v.join(", ");
  if (typeof v === "object") return JSON.stringify(v);
  return String(v);
}

function splitCsv(input: string): string[] {
  return input
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
}

/**
 * Convert Shopify GID to plain numeric id if possible.
 * Example: gid://shopify/Product/8112925769802 -> 8112925769802
 */
function toPlainId(raw: any): string {
  const text = toStringValue(raw).trim();
  if (!text) return "";
  const match = text.match(/\/(\d+)(\?.*)?$/);
  if (match?.[1]) return match[1];
  return text;
}

/**
 * Normalize field path to dot style.
 * Example: metafields[0].value -> metafields.0.value
 */
function normalizeFieldPath(path: string): string {
  return path.replace(/\[(\d+)\]/g, ".$1").replace(/^\./, "");
}

export default function DetailPage() {
  const { productId } = useParams();
  const location = useLocation() as any;

  const [accountId, setAccountId] = useState<number | null>(location?.state?.accountId ?? null);
  const [productData, setProductData] = useState<any>(location?.state?.productData ?? null);

  const [loadingDetail, setLoadingDetail] = useState(false);

  // Summary state
  const [summary, setSummary] = useState<SummaryData>({});
  const [editableSummary, setEditableSummary] = useState<Record<EditableSummaryField, string>>({
    title: "",
    vendor: "",
    productType: "",
    tags: "",
    descriptionHtml: "",
  });

  // Metafield state (value editable)
  const [metafields, setMetafields] = useState<MetafieldItem[]>([]);
  const [metafieldValues, setMetafieldValues] = useState<Record<number, string>>({});

  // Validation / commit state
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [validation, setValidation] = useState<any>(null);
  const [validating, setValidating] = useState(false);
  const [committing, setCommitting] = useState(false);

  useEffect(() => {
    if (!accountId) {
      (async () => {
        try {
          const res = await listAccounts();
          const data = res?.data || [];
          if (!data.length) return;
          const def = data.find((a: any) => a.isDefault) || data[0];
          setAccountId(def.id);
        } catch {
          // Ignore here
        }
      })();
    }
  }, [accountId]);

  useEffect(() => {
    if (!productData && accountId && productId) {
      (async () => {
        try {
          setLoadingDetail(true);
          const res = await fetchProductById({
            accountId,
            productId: String(productId),
          });
          if (!res.success) throw new Error(res.message || "Failed to fetch product detail");
          setProductData(res.data);
        } catch (e: any) {
          message.error(e.message || "Failed to load detail data");
        } finally {
          setLoadingDetail(false);
        }
      })();
    }
  }, [productData, accountId, productId]);

  useEffect(() => {
    if (!productData) {
      setSummary({});
      setEditableSummary({
        title: "",
        vendor: "",
        productType: "",
        tags: "",
        descriptionHtml: "",
      });
      setMetafields([]);
      setMetafieldValues({});
      setFieldErrors({});
      setValidation(null);
      return;
    }

    const s = productData?.view?.summary || {};
    const mfs = (productData?.view?.metafields || []) as MetafieldItem[];

    const normalizedSummary: SummaryData = {
      id: toPlainId(s.id),
      title: s.title || "",
      handle: s.handle || "",
      status: s.status || "",
      vendor: s.vendor || "",
      productType: s.productType || "",
      tags: Array.isArray(s.tags) ? s.tags : toStringValue(s.tags),
      descriptionHtml: s.descriptionHtml || "",
      createdAt: s.createdAt || "",
      updatedAt: s.updatedAt || "",
    };

    setSummary(normalizedSummary);
    setEditableSummary({
      title: toStringValue(normalizedSummary.title),
      vendor: toStringValue(normalizedSummary.vendor),
      productType: toStringValue(normalizedSummary.productType),
      tags: Array.isArray(normalizedSummary.tags)
        ? normalizedSummary.tags.join(", ")
        : toStringValue(normalizedSummary.tags),
      descriptionHtml: toStringValue(normalizedSummary.descriptionHtml),
    });

    const normalizedMetafields = mfs.map((mf) => ({
      id: toPlainId(mf.id),
      namespace: mf.namespace || "",
      key: mf.key || "",
      type: mf.type || "",
      value: toStringValue(mf.value),
    }));
    setMetafields(normalizedMetafields);

    const initialValues: Record<number, string> = {};
    normalizedMetafields.forEach((mf, idx) => {
      initialValues[idx] = toStringValue(mf.value);
    });
    setMetafieldValues(initialValues);

    setFieldErrors({});
    setValidation(null);
  }, [productData]);

  const onChangeSummary = (field: EditableSummaryField, value: string) => {
    setEditableSummary((prev) => ({ ...prev, [field]: value }));
  };

  const onChangeMetafieldValue = (idx: number, value: string) => {
    setMetafieldValues((prev) => ({ ...prev, [idx]: value }));
  };

  const updatePayload = useMemo(() => {
    return {
      productId: String(productId || ""),
      title: editableSummary.title,
      vendor: editableSummary.vendor,
      productType: editableSummary.productType,
      tags: splitCsv(editableSummary.tags),
      descriptionHtml: editableSummary.descriptionHtml,
      metafields: metafields.map((mf, idx) => ({
        namespace: mf.namespace || "",
        key: mf.key || "",
        type: mf.type || "",
        value: metafieldValues[idx] ?? "",
      })),
    };
  }, [productId, editableSummary, metafields, metafieldValues]);

  const applyInlineErrors = (issues: any[] = []) => {
    const next: Record<string, string> = {};

    issues.forEach((it) => {
      const raw = String(it?.fieldPath || "").trim();
      if (!raw) return;
      const msg = it?.message || "Validation failed";
      next[raw] = msg;
      next[normalizeFieldPath(raw)] = msg;
    });

    setFieldErrors(next);
  };

  const onValidate = async () => {
    try {
      setValidating(true);
      const res = await runValidation(updatePayload);
      if (!res.success) throw new Error(res.message || "Validate failed");

      setValidation(res.data);
      applyInlineErrors(res?.data?.issues || []);
      message.success(res?.data?.pass ? "Validation passed" : "Validation has issues");
    } catch (e: any) {
      message.error(e.message || "Validate failed");
    } finally {
      setValidating(false);
    }
  };

  const onCommit = async () => {
    if (!accountId) {
      message.error("Account is not available");
      return;
    }
    try {
      setCommitting(true);
      const res = await commitProduct({
        accountId: Number(accountId),
        productId: String(productId || ""),
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

  const getSummaryError = (field: string) => fieldErrors[field] || "";

  const summaryTableData: SummaryRow[] = [
    { rowKey: "summary-id", field: "id", value: toPlainId(summary.id), editable: false, error: getSummaryError("id"), bindField: "" },
    { rowKey: "summary-handle", field: "handle", value: toStringValue(summary.handle), editable: false, error: getSummaryError("handle"), bindField: "" },
    { rowKey: "summary-status", field: "status", value: toStringValue(summary.status), editable: false, error: getSummaryError("status"), bindField: "" },
    { rowKey: "summary-createdAt", field: "createdAt", value: toStringValue(summary.createdAt), editable: false, error: getSummaryError("createdAt"), bindField: "" },
    { rowKey: "summary-updatedAt", field: "updatedAt", value: toStringValue(summary.updatedAt), editable: false, error: getSummaryError("updatedAt"), bindField: "" },
    { rowKey: "summary-title", field: "title", value: editableSummary.title, editable: true, error: getSummaryError("title"), bindField: "title" },
    { rowKey: "summary-vendor", field: "vendor", value: editableSummary.vendor, editable: true, error: getSummaryError("vendor"), bindField: "vendor" },
    { rowKey: "summary-productType", field: "productType", value: editableSummary.productType, editable: true, error: getSummaryError("productType"), bindField: "productType" },
    { rowKey: "summary-tags", field: "tags", value: editableSummary.tags, editable: true, error: getSummaryError("tags"), bindField: "tags" },
    { rowKey: "summary-descriptionHtml", field: "descriptionHtml", value: editableSummary.descriptionHtml, editable: true, error: getSummaryError("descriptionHtml"), bindField: "descriptionHtml" },
  ];

  const onEditSummaryValue = (row: SummaryRow, newValue: string) => {
    if (!row.editable || !row.bindField) return;
    onChangeSummary(row.bindField, newValue);
  };

  // Summary uses spacer columns (no business meaning) only for geometry alignment
  const summaryColumns: ColumnsType<SummaryRow> = [
    {
      title: "field",
      dataIndex: "field",
      key: "field",
      width: COL_1,
      render: (v: string) => <Text strong>{v}</Text>,
    },
    {
      title: "value",
      dataIndex: "value",
      key: "value",
      width: COL_2,
      render: (_: string, row) =>
        row.editable ? (
          row.bindField === "descriptionHtml" ? (
            <Input.TextArea rows={3} value={row.value} onChange={(e) => onEditSummaryValue(row, e.target.value)} />
          ) : (
            <Input value={row.value} onChange={(e) => onEditSummaryValue(row, e.target.value)} />
          )
        ) : (
          <Text>{row.value || "-"}</Text>
        ),
    },
    {
      title: "",
      dataIndex: "rowKey",
      key: "spacer1",
      width: COL_3,
      render: () => null,
    },
    {
      title: "",
      dataIndex: "rowKey",
      key: "spacer2",
      width: COL_4,
      render: () => null,
    },
    {
      title: "Validation Result",
      dataIndex: "error",
      key: "error",
      width: COL_5,
      render: (v: string) => (v ? <Text style={{ color: "red" }}>{v}</Text> : null),
    },
  ];

  const errorForMetafieldRow = (idx: number): string => {
    const candidates = [
      `metafields.${idx}.value`,
      `metafields[${idx}].value`,
      `metafields.${idx}.namespace`,
      `metafields[${idx}].namespace`,
      `metafields.${idx}.key`,
      `metafields[${idx}].key`,
      `metafields.${idx}.type`,
      `metafields[${idx}].type`,
    ];
    for (const p of candidates) {
      if (fieldErrors[p]) return fieldErrors[p];
    }
    return "";
  };

  const metafieldTableData: MetafieldRow[] = useMemo(
    () =>
      metafields.map((mf, idx) => ({
        rowKey: `mf-row-${idx}`,
        index: idx,
        namespace: mf.namespace || "",
        key: mf.key || "",
        type: mf.type || "",
        value: metafieldValues[idx] ?? "",
        error: errorForMetafieldRow(idx),
      })),
    [metafields, metafieldValues, fieldErrors]
  );

  const metafieldColumns: ColumnsType<MetafieldRow> = [
    { title: "namespace", dataIndex: "namespace", key: "namespace", width: COL_1, render: (v: string) => <Text>{v || "-"}</Text> },
    { title: "key", dataIndex: "key", key: "key", width: COL_2, render: (v: string) => <Text>{v || "-"}</Text> },
    { title: "type", dataIndex: "type", key: "type", width: COL_3, render: (v: string) => <Text>{v || "-"}</Text> },
    {
      title: "value",
      dataIndex: "value",
      key: "value",
      width: COL_4,
      render: (_: string, row) => (
        <Input value={row.value} onChange={(e) => onChangeMetafieldValue(row.index, e.target.value)} />
      ),
    },
    {
      title: "Validation Result",
      dataIndex: "error",
      key: "error",
      width: COL_5,
      render: (v: string) => (v ? <Text style={{ color: "red" }}>{v}</Text> : null),
    },
  ];

  return (
    <Space direction="vertical" style={{ width: "100%" }} size={16}>
      <Card title="Detail" loading={loadingDetail}>
        {!productData ? (
          <Text type="secondary">No data loaded yet.</Text>
        ) : (
          <>
            <Card size="small" title="Summary" style={{ marginBottom: 12 }}>
              <Table<SummaryRow>
                rowKey="rowKey"
                columns={summaryColumns}
                dataSource={summaryTableData}
                pagination={false}
                size="small"
                tableLayout="fixed"
                scroll={{ x: TABLE_X }}
              />
            </Card>

            <Card size="small" title="Metafields">
              <Table<MetafieldRow>
                rowKey="rowKey"
                columns={metafieldColumns}
                dataSource={metafieldTableData}
                pagination={false}
                size="small"
                tableLayout="fixed"
                scroll={{ x: TABLE_X }}
                locale={{ emptyText: "No metafields" }}
              />
            </Card>

            <Space style={{ marginTop: 14 }}>
              <Button onClick={onValidate} loading={validating}>
                Validate
              </Button>
              <Button type="primary" onClick={onCommit} loading={committing}>
                Commit
              </Button>
            </Space>
          </>
        )}
      </Card>

      <Card title="Validation Result">
        {!validation ? (
          <Text type="secondary">Click Validate to check current edits.</Text>
        ) : validation.pass ? (
          <Alert type="success" message="Validation passed" showIcon />
        ) : (
          <Alert
            type="error"
            message={`Validation failed (${validation.failed || 0} issues)`}
            description={
              <ul style={{ marginTop: 8, marginBottom: 0, paddingLeft: 20 }}>
                {(validation.issues || []).map((it: any, idx: number) => (
                  <li key={idx} style={{ color: "red" }}>
                    [{it.fieldPath}] {it.message}
                  </li>
                ))}
              </ul>
            }
            showIcon
          />
        )}
      </Card>
    </Space>
  );
}
