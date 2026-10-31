import { handleMatch, handleSmsWebhook, json } from "./handlers.js";

function pathOf(request) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  return path;
}

export default {
  async fetch(request, env) {
    const path = pathOf(request);
    const method = request.method.toUpperCase();

    if (method === "GET" && path === "/") {
      return json({ ok: true, service: "mj-pay-gateway" });
    }

    if (method === "POST" && path === "/sms-webhook") {
      return handleSmsWebhook(request, env);
    }

    if (method === "POST" && path === "/v1/match") {
      return handleMatch(request, env);
    }

    if (method !== "POST" && (path === "/sms-webhook" || path === "/v1/match")) {
      return json({ ok: false, error: "method not allowed" }, 405);
    }

    return json({ ok: false, error: "not found" }, 404);
  },
};
