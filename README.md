
# TFTP Client-Server Simulator (Java TCP & UDP)

This project implements a Trivial File Transfer Protocol (TFTP) simulation using Java over both UDP and TCP sockets. Developed as part of a computer networks coursework, it supports file uploads and downloads, error handling, and retransmission logic.

## 🔧 Features
- TFTP over UDP and TCP with separate clients and servers
- Concurrency via multithreaded request handlers
- Custom timeout and retransmission for UDP
- Simplified TCP protocol handling using stream sockets
- Human-readable error messages for debugging
- Includes test files and batch run configurations

## 📁 Structure
```
TFTP-TCP-Client/        # TCP Client (Java + Maven)
TFTP-TCP-Server/        # TCP Server (Java + Maven)
TFTP-UDP-Client/        # UDP Client (Java + Maven)
TFTP-UDP-Server/        # UDP Server (Java + Maven)
Report/                 # Coursework documentation
```

## 🚀 How to Run
1. Navigate to the respective folder (`TFTP-UDP-Client`, etc.)
2. Compile using Maven:
   ```bash
   mvn clean compile
   ```
3. Run the server:
   ```bash
   java -cp target/classes TFTPUDPServer
   ```
4. Run the client:
   ```bash
   java -cp target/classes TFTPUDPClient <serverIP> get <filename>
   java -cp target/classes TFTPUDPClient <serverIP> put <filename>
   ```

## 📋 Requirements
- Java 11+
- Maven 3+

## 📄 Report
See `Report/` for detailed protocol descriptions, testing results, and implementation notes.

## 👤 Author
AndreasChatzopoulos
