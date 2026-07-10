# Flint Designer Bridge

> **Breaking change in v1.0.0**
>
> The module ID was renamed from `com.bwdesigngroup.flint-designer-bridge`
> to `dev.bwdesigngroup.flint.FlintDesignerBridge` to align with
> Ignition's reverse-DNS convention. Gateways with v0.13.x or earlier
> installed must uninstall the old module before installing v1.0.0 or later —
> the gateway sees the new ID as a separate module. The Java package
> path was also renamed from `com.bwdesigngroup.ignition.flint.*` to
> `dev.bwdesigngroup.flint.*` for consistency.

An Ignition module that enables the [Flint VS Code Extension](https://github.com/bw-design-group/flint-vscode-extension) to connect to running Designer instances for script execution and debugging, and exposes a headless Gateway HTTP transport with a full Jython language server.

![Version](https://img.shields.io/badge/version-1.1.1-blue.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)
![Ignition](https://img.shields.io/badge/Ignition-8.1.44+%20%7C%208.3.1+-orange.svg)

**Documentation:** Full docs are available at [flint.docs.bwdesigngroup.dev](https://flint.docs.bwdesigngroup.dev).

## Overview

Flint Designer Bridge provides a WebSocket-based communication layer between VS Code and Ignition Designer, plus a headless Gateway HTTP transport, enabling:

- **Script Execution**: Run Python scripts directly from VS Code in the Designer or Gateway scope
- **Debug Sessions**: Full debugging support with breakpoints, stepping, and variable inspection
- **Output Capture**: Real-time stdout/stderr streaming to the VS Code debug console
- **Session Management**: Persistent variable contexts across script executions
- **Headless Gateway endpoint** (v1.1.0+): a `POST /data/flint/rpc` transport and `GET /data/flint/health` discovery endpoint that work without a running Designer
- **Gateway Jython language server** (v1.1.0+): full completion, hover, go-to-definition, references, symbols, and syntax diagnostics served from the Gateway (the Designer WebSocket LSP is completion-only)
- **Native LSP over WebSocket** (v1.2.0+): the language server speaks the standard Language Server Protocol at `wss://<gateway>/system/flint-lsp`, so VS Code — or any LSP-capable editor — connects directly with a gateway API token

## Requirements

- Ignition **8.1.44+** (install the `-8.1` artifact) or **8.3.1+** (install the `-8.3` artifact)
- [Flint VS Code Extension](https://github.com/bw-design-group/flint-vscode-extension)

## Installation

### From GitHub Release

Each release ships two artifacts. Download the one matching your Gateway from the [Releases](https://github.com/bw-design-group/flint-designer-bridge-ignition-module/releases) page:

- `Flint-Designer-Bridge-<version>-8.1.modl` — for Ignition 8.1.44+
- `Flint-Designer-Bridge-<version>-8.3.modl` — for Ignition 8.3.1+

1. Download the artifact for your Ignition version
2. In your Ignition Gateway, go to **Config → Modules**
3. Click **Install or Upgrade a Module**
4. Upload the `.modl` file
5. Accept the license and install

### Building from Source

```bash
# Clone the repository
git clone https://github.com/bw-design-group/flint-designer-bridge-ignition-module.git
cd flint-designer-bridge-ignition-module

# Build the module (unsigned)
./gradlew build

# The unsigned module will be at: build/Flint-Designer-Bridge.unsigned.modl
```

## How It Works

When a Designer is launched, the module:

1. Starts a WebSocket server on a dynamically assigned port
2. Writes a connection file to `~/.ignition/flint/designers/` containing:
   - WebSocket port number
   - Authentication secret
   - Project name
   - Designer process ID
3. VS Code discovers this file and connects to the Designer
4. All communication uses JSON-RPC 2.0 over WebSocket

### Security

- Each Designer instance generates a unique authentication secret
- The secret is stored in a file only readable by the current user
- All JSON-RPC requests require prior authentication
- Connection files are cleaned up when Designer closes

## Gateway API Authentication

In addition to the Designer WebSocket bridge, the module exposes a headless
HTTP transport on the Gateway:

| Endpoint | Method | Auth |
|----------|--------|------|
| `/data/flint/rpc` | POST | Required (see below) |
| `/data/flint/health` | GET | None (capability discovery) |
| `/system/flint-lsp` | WebSocket | Required — same schemes, sent as headers on the upgrade (v1.2.0+) |

How you authenticate depends on the Ignition version.

### Ignition 8.3+ — native API tokens

Ignition 8.3 has platform API tokens built in. Create one in the Gateway web
UI (**Config → Security → API Tokens**, or provision it as an
`ignition/api-token` config resource) and send it on each request:

```bash
curl -H "X-Ignition-API-Token: <keyId>:<secret>" \
     -X POST https://<gateway>/data/flint/rpc
```

The Flint-managed bearer token described below also works on 8.3 as a
portable fallback, so a client written against the 8.1 scheme needs no
changes.

### Ignition 8.1 — Flint-managed bearer token

Ignition 8.1 has no native API tokens, so the module manages its own shared
secret and accepts it as a bearer token:

```bash
curl -H "Authorization: Bearer <token>" \
     -X POST https://<gateway>/data/flint/rpc
```

At startup the module resolves the token in this order:

1. **Operator-supplied** — the `flint.gateway.apiToken` JVM system property
   (add a `wrapper.java.additional` entry in `ignition.conf`) or the
   `FLINT_GATEWAY_API_TOKEN` environment variable (convenient in Docker).
   Operator-supplied tokens are used verbatim and never written to disk.
2. **Previously persisted** — an existing token file (see below).
3. **Auto-generated** — a fresh 48-character hex token, generated and
   persisted on first startup.

#### Retrieving the generated token

The token is deliberately never exposed over HTTP and never printed to the
Gateway logs — reading it requires filesystem access to the Gateway, which is
the security boundary. It lives in the Gateway data directory:

```
<ignition-data-dir>/modules/flint/gateway/api-token.json
```

(typically `/usr/local/bin/ignition/data/modules/flint/gateway/api-token.json`
on a standard Linux install), as `{"token": "<value>"}` with `0600`
permissions.

#### Rotating the token

Delete `api-token.json` and restart the Gateway (or the module); a fresh
token is generated and persisted. If the token was operator-supplied, change
the system property or environment variable instead.

### Client configuration

Clients (the Flint VS Code extension and the Flint MCP server / language
server proxy) read the token from a local token file and pick the header
automatically: bearer for 8.1, native API token for 8.3. Keep that token file
out of version control — it is a plaintext secret.

## Development

### Project Structure

```
flint-ignition-module/
├── common/          # Shared code between gateway and designer
├── designer/        # Designer scope module
│   └── handlers/    # JSON-RPC method handlers
├── gateway/         # Gateway scope module
└── docker/          # Development environment
```

### Local Development

1. Start the development gateway:
   ```bash
   cd docker
   docker-compose up -d
   ```

2. Deploy the module:
   ```bash
   ./gradlew deploy -PhostGateway=https://localhost:8443
   ```

3. Launch a Designer and verify the connection file is created

### Debugging the Module

Enable debug logging in the gateway:
- Go to **Config → Gateway Settings → Gateway → Logging**
- Set `dev.bwdesigngroup.flint` to DEBUG

## JSON-RPC Methods

The module exposes a large JSON-RPC surface (roughly 70 methods) grouped by
capability. Representative core methods:

| Method | Description |
|--------|-------------|
| `authenticate` | Authenticate with the Designer instance |
| `ping` | Check connection status |
| `executeScript` | Execute Python code in Designer or Gateway scope |
| `debug.*` | Debug session control: `start`, `setBreakpoints`, `continue`, `stepIn`/`stepOut`/`stepOver`, `evaluate`, `getStackTrace`, `getScopes`, `getVariables` |
| `lsp.*` | Jython language server: completion, hover, definition, references, symbols, and syntax diagnostics |

Beyond these, the module serves methods for tags, Perspective views and
components, UDTs, project resources, and icons. See the full method reference
at [flint.docs.bwdesigngroup.dev](https://flint.docs.bwdesigngroup.dev).

## Contributing

Contributions are welcome! Please see the [Flint VS Code Extension](https://github.com/bw-design-group/flint-vscode-extension) repository for contribution guidelines.

## License

MIT License - see [LICENSE.txt](LICENSE.txt) for details.

## Related Projects

- [Flint VS Code Extension](https://github.com/bw-design-group/flint-vscode-extension) - The VS Code extension that uses this module

## Support

- **Issues**: [GitHub Issues](https://github.com/bw-design-group/flint-designer-bridge-ignition-module/issues)
- **Discussions**: [GitHub Discussions](https://github.com/bw-design-group/flint-designer-bridge-ignition-module/discussions)

---

**Note**: This module is not officially affiliated with Inductive Automation. Ignition is a trademark of Inductive Automation.
