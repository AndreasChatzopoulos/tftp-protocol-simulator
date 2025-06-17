package server;

import java.io.*;
import java.net.*;


public class TFTPTCPServer {

    private static final int port = 9019;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("TCP TFTP Server is listening on port " + port);

            // Server is listening indefinitely for incoming client connections
            while (true) {
                Socket clientSocket = serverSocket.accept(); // wait for client connection
                new Thread(new ClientHandler(clientSocket)).start(); // Each client handled by a new thread
            }
        } catch (IOException e) {
            System.err.println("Server encountered an error: " + e.getMessage());
        }
    }

    /**
     * class handles client requests for reading and writing files.
     */
    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                 DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

                int opcode = in.readShort();  // read the opcode .
                String fileName = in.readUTF(); // read the filename

                if (opcode == 1) {
                    handleReadRequest(fileName, out);
                } else if (opcode == 2) {
                    handleWriteRequest(fileName, in);
                } else {
                    sendError(out, "Invalid request.");
                }
            } catch (IOException e) {
                System.err.println("Client handling failed: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException ignored) {}
            }
        }

        private void handleReadRequest(String fileName, DataOutputStream out) throws IOException {
            File file = new File(fileName);
            if (!file.exists()) {
                sendError(out, "File not found.");
                return;
            }

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[512];
                int bytesRead;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    out.writeShort(bytesRead);
                    out.write(buffer, 0, bytesRead);
                }
                out.writeShort(0); // indicate that teh file ends
            }
        }

        private void handleWriteRequest(String fileName, DataInputStream in) throws IOException {
            try (FileOutputStream fos = new FileOutputStream(fileName)) {
                int bytesRead;
                byte[] buffer = new byte[512];

                while ((bytesRead = in.readShort()) != 0) {
                    in.readFully(buffer, 0, bytesRead);
                    fos.write(buffer, 0, bytesRead);
                }
            }
        }

        private void sendError(DataOutputStream out, String errorMsg) throws IOException {
            out.writeShort(-1); // means error
            out.writeUTF(errorMsg);
        }
    }
}
