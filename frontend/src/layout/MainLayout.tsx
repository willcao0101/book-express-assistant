import { AppstoreOutlined, DatabaseOutlined, SettingOutlined, UnorderedListOutlined } from "@ant-design/icons";
import { Layout, Menu, Typography } from "antd";
import { Outlet, useLocation, useNavigate } from "react-router-dom";

const { Sider, Header, Content } = Layout;
const { Title, Text } = Typography;

export default function MainLayout() {
  const navigate = useNavigate();
  const location = useLocation();

  const items = [
    { key: "/product", icon: <AppstoreOutlined />, label: "Product" },
    { key: "/records", icon: <UnorderedListOutlined />, label: "Records" },
    { key: "/settings", icon: <SettingOutlined />, label: "Settings" },
  ];

  return (
    <Layout style={{ minHeight: "100vh" }}>
      <Sider width={220} theme="light" style={{ borderRight: "1px solid #f0f0f0" }}>
        <div style={{ padding: 16 }}>
          <Title level={4} style={{ margin: 0 }}>BookExpress</Title>
          <Text type="secondary">Shopify Data Console</Text>
        </div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname.startsWith("/detail") ? "/product" : location.pathname]}
          items={items}
          onClick={(e) => navigate(e.key)}
        />
      </Sider>

      <Layout>
        <Header style={{ background: "#fff", borderBottom: "1px solid #f0f0f0", padding: "0 16px" }}>
          <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <DatabaseOutlined style={{ marginRight: 8 }} />
            <Text>Product governance, validation and commit workflow</Text>
          </div>
        </Header>

        <Content style={{ padding: 16 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
