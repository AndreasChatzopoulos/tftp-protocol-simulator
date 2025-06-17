package server;

import java.io.*;
import java.net.*;

public class TFTPUDPServer {

    private static final int port = 9000;
    private static final int packet_size = 516;

    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("TFTP Server is listening on port " + port);

            while (true) {
                DatagramPacket request = new DatagramPacket(new byte[packet_size], packet_size);
                socket.receive(request);
                new Thread(new RequestHandler(request)).start();
            }
        } catch (IOException e) {
            System.err.println("Server encountered an error: " + e.getMessage());
        }
    }

    private static class RequestHandler implements Runnable {

        private static final int op_read_request = 1;
        private static final int op_write_request = 2;
        private static final int op_data = 3;
        private static final int op_acknowledgment = 4;
        private static final int op_error = 5;

        private DatagramPacket request;

        RequestHandler(DatagramPacket request) {
            this.request = request;
        }

        @Override
        public void run() {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(3000); // 3 seconds
                byte[] data = request.getData();
                int opcode = ((data[0] & 0xff) << 8) | (data[1] & 0xff);

                int index = 2;
                StringBuilder fileName = new StringBuilder();
                while (data[index] != 0) {
                    fileName.append((char) data[index++]);
                }

                if (opcode == op_read_request) {
                    handleReadRequest(fileName.toString(), request, socket);
                } else if (opcode == op_write_request) {
                    handleWriteRequest(fileName.toString(), request, socket);
                } else {
                    sendError(socket, request, "Invalid request.");
                }
            } catch (IOException e) {
                System.err.println("Request handling failed: " + e.getMessage());
            }
        }

        private void handleReadRequest(String fileName, DatagramPacket request, DatagramSocket socket) throws IOException {
            File file = new File(fileName);
            if (!file.exists()) {
                sendError(socket, request, "File not found.");
                return;
            }

            try (FileInputStream fis = new FileInputStream(file)) {
                int blockNumber = 1;
                byte[] buffer = new byte[512];
                int bytesRead;
                do {
                    bytesRead = fis.read(buffer);
                    byte[] sendBuffer = new byte[4 + (bytesRead > 0 ? bytesRead : 0)];
                    sendBuffer[0] = 0;
                    sendBuffer[1] = (byte) op_data;
                    sendBuffer[2] = (byte) (blockNumber >> 8);
                    sendBuffer[3] = (byte) (blockNumber & 0xff);
                    if (bytesRead > 0) {
                        System.arraycopy(buffer, 0, sendBuffer, 4, bytesRead);
                    }

                    DatagramPacket outPacket = new DatagramPacket(sendBuffer, sendBuffer.length, request.getAddress(), request.getPort());
                    socket.send(outPacket);

                    int attempts = 0;
                    boolean acked = false;
                    while (!acked && attempts < 4) {
                        try {
                            DatagramPacket ackPacket = new DatagramPacket(new byte[packet_size], packet_size);
                            socket.receive(ackPacket);
                            acked = true;
                        } catch (SocketTimeoutException ex) {
                            attempts++;
                            socket.send(outPacket);
                        }
                    }

                    blockNumber++;
                } while (bytesRead == 512);
            }
        }

        private void handleWriteRequest(String fileName, DatagramPacket request, DatagramSocket socket) throws IOException {
            try (FileOutputStream fos = new FileOutputStream(fileName)) {
                sendAck(socket, request, 0);

                int blockNumber = 1;
                byte[] recvBuffer = new byte[packet_size];

                while (true) {
                    DatagramPacket packet = new DatagramPacket(recvBuffer, packet_size);
                    int attempts = 0;
                    boolean gotData = false;
                    while (!gotData && attempts < 4) {
                        try {
                            socket.receive(packet);
                            gotData = true;
                        } catch (SocketTimeoutException ex) {
                            attempts++;
                            sendAck(socket, request, (blockNumber - 1));
                        }
                    }
                    if (!gotData) break; // stop

                    int dataLength = packet.getLength() - 4;
                    fos.write(packet.getData(), 4, dataLength);
                    sendAck(socket, request, blockNumber);

                    if (dataLength < 512) {
                        break;
                    }
                    blockNumber++;
                }
            }
        }

        private void sendAck(DatagramSocket socket, DatagramPacket request, int blockNumber) throws IOException {
            byte[] ack = {0, (byte) op_acknowledgment, (byte) (blockNumber >> 8), (byte) (blockNumber & 0xff)};
            socket.send(new DatagramPacket(ack, ack.length, request.getAddress(), request.getPort()));
        }

        private void sendError(DatagramSocket socket, DatagramPacket request, String errorMsg) throws IOException {
            byte[] msgBytes = errorMsg.getBytes();
            byte[] errorBuffer = new byte[4 + msgBytes.length + 1];
            errorBuffer[0] = 0;
            errorBuffer[1] = (byte) op_error;
            errorBuffer[2] = 0;
            errorBuffer[3] = 1;
            System.arraycopy(msgBytes, 0, errorBuffer, 4, msgBytes.length);
            errorBuffer[errorBuffer.length - 1] = 0;
            socket.send(new DatagramPacket(errorBuffer, errorBuffer.length, request.getAddress(), request.getPort()));
        }
    }
}
