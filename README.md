# Flint Designer Bridge

> **Breaking change in v1.0.0**
>
> The module ID was renamed from `com.bwdesigngroup.flint-designer-bridge`
> to `dev.bwdesigngroup.flint.FlintDesignerBridge` to align with
> Ignition's reverse-DNS convention. Gateways with v0.13.x or earlier
> installed must uninstall the old module before installing v1.0.0 —
> the gateway sees the new ID as a separate module. The Java package
> path was also renamed from `com.bwdesigngroup.ignition.flint.*` to
> `dev.bwdesigngroup.flint.*` for consistency.

An Ignition module that enables the [Flint VS Code Extension](https://github.com/bw-design-group/flint-vscode-extension) to connect to running Designer instances for script execution and debugging.

![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)
![Ignition](https://img.shields.io/badge/Ignition-8.1.0+-orange.svg)

## Overview

Flint Designer Bridge provides a WebSocket-based communication layer between VS Code and Ignition Designer, enabling:

- **Script Execution**: Run Python scripts directly from VS Code in the Designer or Gateway scope
- **Debug Sessions**: Full debugging support with breakpoints, stepping, and variable inspection
- **Output Capture**: Real-time stdout/stderr streaming to the VS Code debug console
- **Session Management**: Persistent variable contexts across script executions

## Requirements

- Ignition 8.1.0 or later
- [Flint VS Code Extension](https://github.com/bw-design-group/flint-vscode-extension)

## Installation

### From GitHub Release

1. Download the latest `Flint-Designer-Bridge.modl` from the [Releases](https://github.com/bw-design-group/flint-designer-bridge-ignition-module/releases) page
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
2. Writes a connection file to `~/.flint/designer-instances/` containing:
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

The module exposes the following JSON-RPC methods:

| Method | Description |
|--------|-------------|
| `authenticate` | Authenticate with the Designer instance |
| `ping` | Check connection status |
| `executeScript` | Execute Python code in Designer or Gateway scope |
| `debug.start` | Start a debug session |
| `debug.setBreakpoints` | Set breakpoints for debugging |
| `debug.continue` | Continue execution |
| `debug.stepIn` | Step into function |
| `debug.stepOut` | Step out of function |
| `debug.stepOver` | Step over line |
| `debug.evaluate` | Evaluate expression in current frame |
| `debug.getStackTrace` | Get current stack trace |
| `debug.getScopes` | Get variable scopes |
| `debug.getVariables` | Get variables in a scope |

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
