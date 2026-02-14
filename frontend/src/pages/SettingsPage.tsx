import { Button, Card, Form, Input, Modal, Space, Switch, Table, message } from "antd";
import { useEffect, useState } from "react";
import { createAccount, listAccounts, updateAccount } from "../api/settings";

export default function SettingsPage() {
  const [list, setList] = useState<any[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<any>(null);
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

  const openEdit = (row: any) => {
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
      message.error(e.message || "Save failed");
    }
  };

  return (
    <Space direction="vertical" style={{ width: "100%" }} size={16}>
      <Card
        title="Account Settings (Client ID / Secret / Token)"
        extra={<Button type="primary" onClick={openCreate}>Add Account</Button>}
      >
        <Table
          rowKey="id"
          dataSource={list}
          columns={[
            { title: "ID", dataIndex: "id", width: 80 },
            { title: "Email", dataIndex: "email" },
            { title: "Shop Domain", dataIndex: "shopDomain" },
            { title: "Client ID", dataIndex: "clientId" },
            { title: "Default", dataIndex: "isDefault", render: (v) => (v ? "Yes" : "No"), width: 90 },
            {
              title: "Action",
              width: 120,
              render: (_, row) => <Button onClick={() => openEdit(row)}>Edit</Button>,
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
          <Form.Item name="email" label="Email" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="shopDomain" label="Shop Domain" rules={[{ required: true }]}>
            <Input placeholder="xxx.myshopify.com" />
          </Form.Item>
          <Form.Item name="clientId" label="Client ID" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="clientSecret" label="Client Secret">
            <Input.Password placeholder={editing ? "Leave empty to keep unchanged" : ""} />
          </Form.Item>
          <Form.Item name="accessToken" label="Access Token">
            <Input.Password placeholder={editing ? "Leave empty to keep unchanged" : ""} />
          </Form.Item>
          <Form.Item name="isDefault" label="Default Account" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
