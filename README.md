# OpenCode Server Manager

Android application and Cloudflare Worker API for connecting to one external OpenCode runtime.

## Architecture
- Android app: Kotlin + Jetpack Compose
- Backend: Cloudflare Workers
- Runtime: separate always-on host running OpenCode
- Worker routes /opencode/* to the runtime and provides /api/status.

**Important:** Cloudflare Workers do not host a permanently running OpenCode process. The OpenCode process must run on separate persistent compute.

## Configure the external OpenCode runtime
Start OpenCode with HTTP Basic authentication enabled. Example environment:
- OPENCODE_SERVER_USERNAME=opencode
- OPENCODE_SERVER_PASSWORD=use-a-long-unique-password

Start the server with: opencode serve --hostname 0.0.0.0 --port 4096

Use HTTPS between the Worker and runtime. Restrict access to the runtime and do not expose an unauthenticated server publicly.

## Deploy the Worker
From the worker directory:
1. npm install
2. npx wrangler login
3. npx wrangler secret put OPENCODE_BASE_URL
4. npx wrangler secret put OPENCODE_PASSWORD
5. npx wrangler secret put MANAGER_API_TOKEN
6. npx wrangler secret put OPENCODE_USERNAME (optional; defaults to opencode)
7. npm run deploy

OPENCODE_BASE_URL is the runtime origin, such as https://runtime.example.net, without /opencode. MANAGER_API_TOKEN must be a separate, long random value. Do not reuse the runtime password.

The Worker URL is your API base. The OpenCode connection URL is:
https://YOUR-WORKER.YOUR-SUBDOMAIN.workers.dev/opencode

For the Android manager's status check, use the Worker URL and MANAGER_API_TOKEN. When connecting from an OpenCode client, use the /opencode URL and the runtime's HTTP Basic username/password.

## Build the APK
Pushes to main trigger GitHub Actions. Open the repository's Actions tab and download the OpenCode-Server-Manager-debug artifact from a successful run. You can also manually run the Build Android APK workflow.

## Security and current scope
- Never commit secrets or embed runtime credentials in the APK.
- Keep MANAGER_API_TOKEN and OPENCODE_PASSWORD private and distinct.
- This initial version manages one existing runtime; it does not provision, start, stop, or restart the external machine.
- The Worker proxy forwards HTTP requests and response streams. Verify WebSocket-dependent routes with the OpenCode client before relying on them.
