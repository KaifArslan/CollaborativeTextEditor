# CRDT Text Editor

A real-time collaborative text editor built around a custom Replicated Growable Array (RGA) CRDT, implemented from scratch with no external CRDT libraries and no central authority over edit order.

[![Video Demo](https://img.youtube.com/vi/aA4ckPK4I1Y/0.jpg)](https://youtu.be/aA4ckPK4I1Y)

## Overview

The CRDT core is an RGA using Lamport clocks for deterministic operation ordering, paired with a JavaFX desktop UI and a TCP relay server for peer synchronization and late-joiner state transfer. Multiple users edit the same document concurrently; changes merge automatically, with no locking and no centralized ordering.

## Features

- Real-time collaborative editing with automatic conflict resolution (RGA CRDT)
- Deterministic ordering of concurrent edits via Lamport clocks
- Eventual Consistency
- Operation batching — fast typing is grouped into a single string operation instead of one per keystroke
- Undo / redo, via buttons or Ctrl+Z / Ctrl+Y
- Range deletion via selection, and word deletion with Ctrl+Backspace
- Cut / copy / paste via keyboard (Ctrl+X/C/V) or right-click context menu
- Peer-to-peer synchronization with late-joiner support (full-state sync)
- Local network (Wi-Fi) collaboration
- Save document as plain text

- Polished JavaFX UI

## Download

**Prebuilt executables (recommended — no Java required):**
Grab the version for your OS from the [latest release](https://github.com/KaifArslan/CollaborativeTextEditor/releases/latest):

- Windows — `CRDTEditor-Kaif-Windows.zip`
- macOS — `CRDTEditor-Kaif-Mac.tar.gz`
- Linux — `CRDTEditor-Kaif-Linux.tar.gz`

Unzip/untar and run — no Java or JavaFX installation needed.(the Linux executable is located inside the bin/ directory).

**Build from source (Maven):**
```bash
git clone https://github.com/KaifArslan/CollaborativeTextEditor.git
cd CollaborativeTextEditor
mvn clean package
```
Requires JDK 25+. Maven resolves the correct platform-specific JavaFX dependencies automatically.

## Using the Editor

1. Start one instance and click **Start as Server**. It binds to `0.0.0.0` and shows the machine's LAN IP and port (`localhost` also works locally).
2. On other machines, click **Start as Client** and enter the host's `IP:PORT`.
3. Type and edit as normal — every insert and delete is sent as a CRDT operation and broadcast to all connected replicas.
4. Joining after edits already exist? The server sends a full CRDT snapshot first, then live operations stream in.
5. Disconnected? Local edits are buffered and replayed automatically on reconnect.

### Keyboard & Mouse

| Action | Shortcut |
|---|---|
| Undo / Redo | `Ctrl+Z` / `Ctrl+Y` |
| Cut / Copy / Paste | `Ctrl+X` / `Ctrl+C` / `Ctrl+V`, or right-click menu |
| Delete word | `Ctrl+Backspace` |

## Networking

- Default port is `9999` — allow it through your OS firewall, or change it in server settings.
- For LAN testing, use the host's local IP (e.g. `192.168.1.5`).
- For internet-wide access you'll need port forwarding or a publicly reachable host — this project doesn't include NAT traversal or authentication.

## Technical Details

The RGA guarantees the standard CRDT properties:

- **Convergence** — all replicas eventually reach the same state
- **Commutativity** — operations apply correctly in any order
- **Associativity** — grouping of operations doesn't affect the result
- **Idempotence** — duplicate operations have no additional effect

Operation ordering is based on Lamport clocks (replacing an earlier UUID-based scheme), giving more consistent, deterministic tie-breaking between concurrent edits.

## Known Limitations

- No authentication or encryption
- Memory usage grows with document size due to tombstone retention

## Future Improvements

- Compressed operation transmission
- Improved cursor synchronization
- Rich text support
