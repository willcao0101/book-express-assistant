import { createBrowserRouter, Navigate } from "react-router-dom";
import MainLayout from "../layout/MainLayout";
import ProductPage from "../pages/ProductPage";
import DetailPage from "../pages/DetailPage";
import RecordsPage from "../pages/RecordsPage";
import SettingsPage from "../pages/SettingsPage";
import OAuthCallbackPage from "../pages/OAuthCallbackPage";
import NotFoundPage from "../pages/NotFoundPage";

const router = createBrowserRouter([
  {
    path: "/",
    element: <MainLayout />,
    children: [
      { index: true, element: <Navigate to="/product" replace /> },
      { path: "product", element: <ProductPage /> },
      { path: "detail/:productId", element: <DetailPage /> },
      { path: "records", element: <RecordsPage /> },
      { path: "settings", element: <SettingsPage /> },
      { path: "oauth/callback", element: <OAuthCallbackPage /> },
    ],
  },
  { path: "*", element: <NotFoundPage /> },
]);

export default router;
