export interface Env {
  OPENCODE_BASE_URL: string;
  OPENCODE_USERNAME?: string;
  OPENCODE_PASSWORD: string;
  MANAGER_API_TOKEN: string;
}
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type, Accept, Last-Event-ID",
};
const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), {
  status, headers: { "Content-Type": "application/json; charset=utf-8", ...cors },
});
function basicAuth(env: Env) {
  return "Basic " + btoa((env.OPENCODE_USERNAME || "opencode") + ":" + env.OPENCODE_PASSWORD);
}
export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    if (!env.OPENCODE_BASE_URL || !env.OPENCODE_PASSWORD || !env.MANAGER_API_TOKEN)
      return json({ error: "Worker secrets are not configured." }, 503);
    if (request.headers.get("Authorization") !== "Bearer " + env.MANAGER_API_TOKEN)
      return json({ error: "Unauthorized" }, 401);

    if (url.pathname === "/api/status") {
      try {
        const healthUrl = new URL("/global/health", env.OPENCODE_BASE_URL);
        const response = await fetch(healthUrl, {
          headers: { Authorization: basicAuth(env) },
          signal: AbortSignal.timeout(8000),
        });
        const body = await response.text();
        let detail: unknown = body.slice(0, 300);
        try { detail = JSON.parse(body); } catch {}
        return json({
          manager: "online",
          opencode: response.ok ? "online" : "unavailable",
          upstreamStatus: response.status,
          detail,
        });
      } catch {
        return json({ manager: "online", opencode: "unreachable" }, 502);
      }
    }

    if (url.pathname !== "/opencode" && !url.pathname.startsWith("/opencode/"))
      return json({ error: "Not found" }, 404);

    const upstreamPath = url.pathname.replace(/^\/opencode/, "") || "/";
    const upstreamUrl = new URL(upstreamPath + url.search, env.OPENCODE_BASE_URL);
    const headers = new Headers(request.headers);
    headers.delete("Authorization");
    headers.delete("Host");
    headers.set("Authorization", basicAuth(env));
    try {
      const upstream = await fetch(new Request(upstreamUrl, {
        method: request.method,
        headers,
        body: ["GET", "HEAD"].includes(request.method) ? undefined : request.body,
        redirect: "manual",
      }));
      const responseHeaders = new Headers(upstream.headers);
      Object.entries(cors).forEach(([key, value]) => responseHeaders.set(key, value));
      responseHeaders.delete("www-authenticate");
      return new Response(upstream.body, {
        status: upstream.status, statusText: upstream.statusText, headers: responseHeaders,
      });
    } catch {
      return json({ error: "Could not reach the OpenCode runtime." }, 502);
    }
  },
};
