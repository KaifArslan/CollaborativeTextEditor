# CRDT Text Editor

A collaborative desktop text editor implementing Conflict-Free Replicated Data Types (CRDTs) to enable real-time synchronization across multiple clients without a central server.

Video Demo:

[![Video Demo](https://img.youtube.com/vi/7PZWaPG3KOM/0.jpg)](https://youtu.be/7PZWaPG3KOM)

## Overview

The CRDT core is a Replicated Growable Array (RGA) implemented from scratch without relying on external CRDT libraries and integrated with a JavaFX desktop UI and a TCP server for message relay and initial state synchronization for distributed text editing. Multiple users can edit the same document concurrently, and changes are merged automatically without conflicts.


## Features

- Real-time collaborative editing
- Automatic conflict resolution using RGA CRDT
- Peer-to-peer synchronization
- Operation-based replication
- Eventual consistency guarantees
- Deterministic ordering of concurrent inserts
- Local network (Wi‑Fi) collaboration support
- Save document as plain text
- Late joiner support via full-state synchronization
- No locks or centralized edit ordering
<p align="center"> <img width="500" height="500" alt="Screenshot 2026-01-23 190742" src="https://github.com/user-attachments/assets/15b7cfcc-50f6-4500-b041-be6344b1e2da" /></p>
<img width="500" height="500" alt="Screenshot 2026-01-23 190830" src="https://github.com/user-attachments/assets/191cfe05-3101-4bbb-a0f8-ccc430088563" />
<img width="500" height="500" alt="Screenshot 2026-01-23 190854" src="https://github.com/user-attachments/assets/46063a45-b229-4418-8b17-5fed59f80212" />


## Running the application: Two Options

### Option A — Run the JAR (if you have Java and JavaFX installed)

**Requirements**

- Java 17+ (tested on Java 21)
- JavaFX SDK matching your Java version
 
Download from the release or directly from this link - https://github.com/KaifArslan/CollaborativeTextEditor/releases/download/v1.0.0/crdt-text-editor.jar 
Run the app. That's it.
Or type in the terminal 
`java -jar crdt-editor.jar`
 
### Option B: Run from Source

**Requirements**
- Java 21+
- JavaFX SDK

**Steps**
1. Clone the repository
2. Configure JavaFX VM options
3. Run the `CRDTEditorApp` class
------

## Using the editor

1. Start one instance and click **Start as Server**. The UI will display the server address (IP and port). By default it binds to `0.0.0.0` and shows the machine’s LAN IP; `localhost` also works.
2. On other machines or instances, click **Start as Client** and enter the server IP (`192.168.x.x:PORT`) shown by the host.
3. Type in any editor window. Edits (insert/delete/newline) are sent as CRDT operations and broadcast to other replicas.
4. If an editor connects after the document already has edits, the server will send a full CRDT snapshot; the client rebuilds its replica then continues receiving live operations.
5. If a client disconnects, local operations are buffered and replayed on reconnect.

### Keyboard / UI notes

- Standard typing works (letters, numbers, punctuation). Common combos such as Ctrl+V (paste) are supported.
- Enter inserts newline characters (`\n`) correctly.

## Networking and firewall notes

- Ensure your OS firewall allows the chosen port (default 9999) or change the port in server settings before starting.
- For LAN tests, use the host machine’s local IP (e.g., `192.168.1.5`) as the server address.
- For public/internet access you must expose the server via port forwarding or host the server in a public cloud. This project does not include NAT traversal or authentication.

## Technical Details

### CRDT Properties

- **Convergence**: All replicas eventually reach the same state
- **Commutativity**: Operations can be applied in any order
- **Associativity**: Grouping of operations does not affect the result
- **Idempotence**: Duplicate operations have no additional effect

---

## Known Limitations

- Authentication or encryption
- Memory usage grows with document size due to tombstone retention

## Future Improvements

- Compressed operation transmission
- Undo / redo support
- Persistent storage    
- Improved cursor synchronization
- Rich text support
