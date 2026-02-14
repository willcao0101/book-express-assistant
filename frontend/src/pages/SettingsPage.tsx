import { Button, Card, Form, Input, Modal, Space, Switch, Table, Tag, message } from "antd";
import { useEffect, useState } from "react";
import { createAccount, listAccounts, oauthStart, updateAccount } from "../api/settings";

type AccountRow = {
  id: number;
  email: string;
  shopDomain: string;
  clientId: string;
  isDefault: boolean;
  hasClientSecret: boolean;
  hasAccessToken: boolean;
};

export default function SettingsPage() {
  const [list, setList] = useState<AccountRow[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<AccountRow | null>(null);
  const [form] = Form.useForm();

  const load = async () => {
    const res = await listAccounts();
    setList(res?.data || []);
  };

  useEffect(() => {
    load();
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ isDefault: false });
    setOpen(true);
  };

  const openEdit = (row: AccountRow) => {
    setEditing(row);
    form.setFieldsValue({
      email: row.email,
      shopDomain: row.shopDomain,
      clientId: row.clientId,
      clientSecret: "",
      accessToken: "",
      isDefault: row.isDefault,
    });
    setOpen(true);
  };

  const onSave = async () => {
    try {
      const values = await form.validateFields();

      if (!values.clientSecret) delete values.clientSecret;
      if (!values.accessToken) delete values.accessToken;

      if (editing) {
        await updateAccount(editing.id, values);
        message.success("Account updated");
      } else {
        await createAccount(values);
        message.success("Account created");
      }

      setOpen(false);
      await load();
    } catch (e: any) {
      message.error(e?.message || "Save failed");
    }
  };

  const onConnectShopify = async (row: AccountRow) => {
    try {
      localStorage.setItem("oauth_account_id", String(row.id));
    //   const redirectUri = `${window.location.origin}/oauth/callback`;
    const redirectUri = import.meta.env.VITE_SHOPIFY_OAUTH_REDIRECT_URI || "http://localhost:5174/oauth/callback";

      const res = await oauthStart({ accountId: row.id, redirectUri });
      if (!res?.success) throw new Error(res?.message || "Failed to start OAuth");

      const authorizeUrl = res?.data?.authorizeUrl as string;
      if (!authorizeUrl) throw new Error("Missing authorizeUrl");

      // Guard against wrong endpoint returned by backend
      if (authorizeUrl.includes("admin.shopify.com/store/")) {
        throw new Error(
          "Invalid OAuth URL returned by backend. It must use https://{shop}.myshopify.com/admin/oauth/authorize"
        );
      }

      window.location.href = authorizeUrl;
    } catch (e: any) {
      message.error(e?.message || "Failed to start OAuth");
    }
  };

  return (
    <Space direction="vertical" style={{ width: "100%" }} size={16}>
      <Card title="Account Settings" extra={<Button type="primary" onClick={openCreate}>Add Account</Button>}>
        <Table
          rowKey="id"
          dataSource={list}
          columns={[
            { title: "ID", dataIndex: "id", width: 80 },
            { title: "Email", dataIndex: "email" },
            { title: "Shop Domain", dataIndex: "shopDomain" },
            { title: "Client ID", dataIndex: "clientId" },
            {
              title: "Secret",
              dataIndex: "hasClientSecret",
              width: 100,
              render: (v: boolean) => (v ? <Tag color="green">Saved</Tag> : <Tag>Missing</Tag>),
            },
            {
              title: "Token",
              dataIndex: "hasAccessToken",
              width: 100,
              render: (v: boolean) => (v ? <Tag color="green">Saved</Tag> : <Tag color="red">Missing</Tag>),
            },
            {
              title: "Default",
              dataIndex: "isDefault",
              width: 90,
              render: (v: boolean) => (v ? "Yes" : "No"),
            },
            {
              title: "Action",
              width: 280,
              render: (_: any, row: AccountRow) => (
                <Space>
                  <Button onClick={() => openEdit(row)}>Edit</Button>
                  <Button onClick={() => onConnectShopify(row)}>Connect Shopify (OAuth)</Button>
                </Space>
              ),
            },
          ]}
        />
      </Card>

      <Modal
        open={open}
        onCancel={() => setOpen(false)}
        onOk={onSave}
        title={editing ? "Edit Account" : "Add Account"}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="email" label="Email" rules={[{ required: true, message: "Email is required" }]}>
            <Input />
          </Form.Item>

          <Form.Item
            name="shopDomain"
            label="Shop Domain"
            rules={[
              { required: true, message: "Shop domain is required" },
              {
                validator: async (_, value) => {
                  if (!value) return;
                  const v = String(value).trim().toLowerCase();
                  if (v.includes("admin.shopify.com/store/")) {
                    return Promise.reject(new Error("Use your-shop.myshopify.com, not admin.shopify.com/store/..."));
                  }
                },
              },
            ]}
          >
            <Input placeholder="book-express-nz.myshopify.com" />
          </Form.Item>

          <Form.Item name="clientId" label="Client ID" rules={[{ required: true, message: "Client ID is required" }]}>
            <Input />
          </Form.Item>

          <Form.Item name="clientSecret" label="Client Secret (optional for update)">
            <Input.Password placeholder={editing ? "Leave empty to keep unchanged" : "Optional"} />
          </Form.Item>

          <Form.Item
            name="accessToken"
            label="Access Token (required for Shopify API calls)"
            extra="Client ID/Secret identify the app; Admin API calls require access token."
          >
            <Input.Password placeholder={editing ? "Leave empty to keep unchanged" : "Optional (or via OAuth)"} />
          </Form.Item>

          <Form.Item name="isDefault" label="Default Account" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
