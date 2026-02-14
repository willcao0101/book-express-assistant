import { Alert, Card, Spin, Typography } from "antd";
import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { oauthCallback } from "../api/settings";

const { Paragraph, Text } = Typography;

export default function OAuthCallbackPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>("");
  const [ok, setOk] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const code = searchParams.get("code") || "";
        const state = searchParams.get("state") || "";
        const errorParam = searchParams.get("error") || "";
        const errorDesc = searchParams.get("error_description") || "";

        if (errorParam) {
          throw new Error(`Shopify OAuth error: ${errorParam}${errorDesc ? ` - ${errorDesc}` : ""}`);
        }
        if (!code) {
          throw new Error("Missing OAuth code in callback URL.");
        }

        const accountIdRaw = localStorage.getItem("oauth_account_id") || "";
        const accountId = Number(accountIdRaw);

        if (!accountId || Number.isNaN(accountId)) {
          throw new Error("Missing account context. Please start OAuth from Settings again.");
        }

        const res = await oauthCallback({
          accountId,
          code,
          state: state || undefined,
        });

        if (!res?.success) {
          throw new Error(res?.message || "OAuth callback failed.");
        }

        setOk(true);
        localStorage.removeItem("oauth_account_id");

        setTimeout(() => {
          navigate("/settings");
        }, 1200);
      } catch (e: any) {
        setError(e.message || "OAuth callback failed.");
      } finally {
        setLoading(false);
      }
    })();
  }, [navigate, searchParams]);

  return (
    <Card title="Shopify OAuth Callback">
      {loading && (
        <>
          <Spin />
          <Paragraph style={{ marginTop: 12 }}>Processing OAuth callback...</Paragraph>
        </>
      )}

      {!loading && ok && (
        <Alert
          type="success"
          showIcon
          message="OAuth success"
          description="Access token has been saved. Redirecting to Settings..."
        />
      )}

      {!loading && !ok && (
        <Alert
          type="error"
          showIcon
          message="OAuth failed"
          description={error || "Unknown error"}
        />
      )}

      <Paragraph style={{ marginTop: 12 }}>
        <Text type="secondary">
          If it fails, go back to Settings and click Connect Shopify again.
        </Text>
      </Paragraph>
    </Card>
  );
}
