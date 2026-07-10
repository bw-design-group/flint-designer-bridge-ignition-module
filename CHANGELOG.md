# Changelog

All notable changes to the Flint Designer Bridge module will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.1] - 2026-07-09

### Changed
- Relicensed under the MIT License.

## [1.1.0] - 2026-07-01

### Added
- Added a headless gateway HTTP JSON-RPC endpoint at `/data/flint/rpc` (with `/data/flint/health`) so external tools can develop Ignition projects with no Designer running.
- Added gateway-scope script execution, tag/UDT operations, Perspective inspection, debug, full project resource + Perspective view CRUD, and Perspective component-registry + icon-library queries over the new endpoint, on both Ignition 8.1 and 8.3.
- Added gateway authentication for the new endpoint: native platform API tokens on 8.3 (`X-Ignition-API-Token`) and a Flint-managed bearer token on 8.1 (`Authorization: Bearer`), overridable via the `flint.gateway.apiToken` system property.
- Added a headless, editor-agnostic Jython (Python 2.7) language server in the gateway scope (`lsp.*` methods): syntax diagnostics, document and workspace symbols, hover, go-to-definition and references (intra-file and cross-file into project scripts), and completion sourcing `system.*` from the live gateway hint tree plus project-script modules — no external stubs and no Designer required.

## [1.0.0] - 2026-05-13
### Changed

- **Breaking:** Module ID renamed from `com.bwdesigngroup.flint-designer-bridge` to `dev.bwdesigngroup.flint.FlintDesignerBridge` to align with Ignition's reverse-DNS convention. Gateways with v0.13.x installed see this release as a separate module and must uninstall the old one before installing v1.0.0.
- **Breaking:** Java package path renamed from `com.bwdesigngroup.ignition.flint.*` to `dev.bwdesigngroup.flint.*` for consistency. The redundant `.ignition` segment was dropped (this is an Ignition module by definition).

## [0.13.6] - 2026-03-16

### Fixed
- Revert WebSocket bind to localhost; require WSL2 mirrored networking [124f27b]

## [0.13.5] - 2026-03-16

### Fixed
- Bind WebSocket to all interfaces for WSL2 connectivity [18a3f8c]

## [0.13.4] - 2026-03-16

### Changed
- Bump version to 0.13.4 [6124b1a]

## [0.13.3] - 2026-03-16

### Added
- Separate lock file from data file for WSL compatibility [6eddde9]

## [0.13.0] - 2026-03-16

### Added
- Multi-version support for Ignition 8.1 and 8.3 [8ff183d]

### Changed
- Bump module version to 0.13.0 [9f8f9e4]
- Bump version to 0.12.1 [4493bf3]

### Fixed
- Include version in release artifact filename [8495a63]
- Match 'Flint Designer Bridge' in gateway logs (not hyphenated) [264818e]
- Use `docker ps -q` without ancestor filter for gateway log check [f67a91a]
- Use docker logs directly in gateway integration test [681774e]
- Remove config/projects volume mounts from CI compose files [da4f5b8]

## [0.12.0] - 2026-02-23

### Added
- View editing handlers: get/set config, get/set component, tree, validate, save, create, delete [adb99c2]
- Component schema introspection: list registered components, get property schemas [adb99c2]
- Icon registry handlers: list icons by library, search by keyword [adb99c2]
- Browser CDP integration: detect and expose JxBrowser DevTools port [adb99c2]
- Designer workspace handlers: open tab discovery, preview mode toggling [adb99c2]
- View catalog handler for project-wide view metadata and embedded view graph [adb99c2]
- View validation against live Perspective component registry [adb99c2]
- JUnit 5 test suite with JaCoCo coverage [ff158ae]
- CI workflow with build, test, and coverage [512a96c]

### Changed
- Update license to proprietary [48edd49]
- Bump version to 0.12.0 [9820e19]

### Fixed
- Skip module signing in CI build step [5ee919e]
- Use zipModule for CI build to avoid signing dependency chain [acd308b]

## [0.11.0] - 2026-02-03

### Added
- Release candidate preparation workflow [c15ffb4]

### Changed
- Prepare for v1.0.0 release [33cb648]

## [0.10.0-RC1] - 2026-02-03

Initial release of the Flint Designer Bridge module.

### Added
- WebSocket server in Designer scope for VS Code extension communication
- JSON-RPC 2.0 protocol for request/response messaging
- Script execution capabilities (Designer, Gateway, Perspective scopes)
- Debug capability for script console integration [b25283c]
- Native LSP (Language Server Protocol) completion support [bbc3a5c]
- Nested script module completion support [ae83337]
- Perspective debug capability support [b50d2eb]

### Fixed
- LSP completion edge cases [aa5c9e3]
