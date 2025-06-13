# 🧠 JServer — Java Multi-Client JSON Socket Server

A lightweight, multithreaded TCP server in pure Java that supports structured communication using JSON over sockets. Built from scratch — no frameworks, no nonsense. Ideal for learning sockets, protocols, and concurrent server design.

---

## 🚀 Features

- 🔌 **Multi-client support** via threads
- 📡 **JSON-based protocol** for structured communication
- 🧵 Per-client thread handling
- 📬 **Broadcast** and **private message** support
- ✅ Basic login handling
- 🔐 (WIP) Extensible for authentication and encryption
- 🧰 Easily extensible and hackable

---

## 📁 Project Structure

JServer/
├── src/
│ ├── server/
│ │ ├── ServerMain.java
│ │ └── ClientHandler.java
│ └── client/
│ └── ClientMain.java
├── lib/
│ └── json-20210307.jar # JSON library
├── build/ # Compiled classes
├── compile.bat # Windows: Compile all sources
├── run-server.bat # Windows: Run the server
├── run-client.bat # Windows: Run the client
└── README.md



---

## 🛠 Requirements

- Java 11 or higher
- JSON.org library (`json-20210307.jar`)
- Terminal or IDE (e.g., IntelliJ, VS Code)

---

## 🧪 Getting Started

### 1. 📦 Compile the Project

```bash
compile.bat

 ▶️ Run the Server
 run-server.bat

💬 Run the Client
run-client.bat
🚧 Known Limitations
❌ No authentication or encryption

⚠️ Unbounded thread creation per client

🚫 Blocking I/O (no async/NIO)

💥 No graceful shutdown

🪵 Console-only logging

🔧 Hardcoded port (9999)

See issues for roadmap and improvements.

🧠 Future Plans
Thread pooling with ExecutorService

Graceful shutdown support

TLS encryption

Logging & monitoring

HTTP/WebSocket version

GUI client

📜 License
This project is under the MIT License. Go wild.

🤘 Author
Built by Ishmael TEMBO — with obsession for low-level control, offensive security, and high-functioning systems.

