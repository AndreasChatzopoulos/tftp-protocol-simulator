package client;

import java.io.*;
import java.net.Socket;


public class TFTPTCPClient {

    private static final int port = 9019; // TCP Server port

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java TFTPTCPClient <serverIP> <get|put> <filename>");
            return;
        }

        String serverIP = args[0];
        String command = args[1];
        String fileName = args[2];

        try (Socket socket = new Socket(serverIP, port);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            if ("get".equals(command)) {
                receiveFile(fileName, in, out);
            } else if ("put".equals(command)) {
                sendFile(fileName, in, out);
            } else {
                System.err.println("Unknown command: " + command);
            }
        } catch (IOException e) {
            System.err.println("I/O Error: " + e.getMessage());
        }
    }

    private static void receiveFile(String fileName, DataInputStream in, DataOutputStream out) throws IOException {
        out.writeShort(1); // read request
        out.writeUTF(fileName);

        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            while (true) {
                int bytesRead = in.readShort();

                if (bytesRead == -1) { // indicates  error
                    String errorMsg = in.readUTF();
                    System.err.println("Server error: " + errorMsg);
                    break;
                }
                if (bytesRead == 0) {
                    break;
                }

                byte[] buffer = new byte[bytesRead];
                in.readFully(buffer);
                fos.write(buffer);
            }
        }
    }

    private static void sendFile(String fileName, DataInputStream in, DataOutputStream out) throws IOException {
        File file = new File(fileName);
        if (!file.exists()) {
            System.err.println("Local file not found.");
            return;
        }

        out.writeShort(2); // write request
        out.writeUTF(fileName);

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[512];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                out.writeShort(bytesRead);
                out.write(buffer, 0, bytesRead);
            }
            out.writeShort(0); // end of file
        }
    }
}
