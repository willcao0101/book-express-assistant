package com.bookexpress.oauth.controller;

import com.bookexpress.oauth.dto.OAuthCallbackRequest;
import com.bookexpress.oauth.dto.OAuthStartRequest;
import com.bookexpress.oauth.service.OAuthService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/oauth")
public class OAuthController {

    private final OAuthService oauthService;

    public OAuthController(OAuthService oauthService) {
        this.oauthService = oauthService;
    }

    @PostMapping("/start")
    public Map<String, Object> start(@RequestBody OAuthStartRequest req) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("message", "OK");
        resp.put("data", oauthService.buildStartUrl(req));
        return resp;
    }

    @PostMapping("/callback")
    public Map<String, Object> callback(@RequestBody OAuthCallbackRequest req) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("message", "OK");
        resp.put("data", oauthService.handleCallback(req));
        return resp;
    }
}
