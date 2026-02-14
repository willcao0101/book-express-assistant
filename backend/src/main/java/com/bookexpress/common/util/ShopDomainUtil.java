package com.bookexpress.shopify.util;

public final class ShopDomainUtil {

    private ShopDomainUtil() {}

    public static String normalize(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toLowerCase();

        if (v.startsWith("https://")) v = v.substring(8);
        if (v.startsWith("http://")) v = v.substring(7);

        int q = v.indexOf('?');
        if (q >= 0) v = v.substring(0, q);
        int h = v.indexOf('#');
        if (h >= 0) v = v.substring(0, h);

        if (v.startsWith("admin.shopify.com/store/")) {
            String shop = v.substring("admin.shopify.com/store/".length());
            int slash = shop.indexOf('/');
            if (slash >= 0) shop = shop.substring(0, slash);
            if (!shop.isBlank()) return shop + ".myshopify.com";
        }

        int slash = v.indexOf('/');
        if (slash >= 0) v = v.substring(0, slash);

        if (!v.contains(".")) return v + ".myshopify.com";
        return v;
    }

    public static boolean isValidMyShopifyDomain(String domain) {
        if (domain == null || domain.isBlank()) return false;
        return domain.matches("^[a-z0-9][a-z0-9-]*\\.myshopify\\.com$");
    }
}
