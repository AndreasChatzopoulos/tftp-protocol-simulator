package client;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;

public class TFTPUDPClient {

    private static final int port = 9000;
    private static final int packet_size = 516;

    private static final int op_read_request = 1;
    private static final int op_write_request = 2;
    private static final int op_data = 3;
    private static final int op_acknowledgment = 4;
    private static final int op_error = 5;

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java TFTPUDPClient <serverIP> <get|put> <filename>");
            return;
        }

        String serverIP = args[0];
        String command = args[1];
        String fileName = args[2];

        try {
            if ("get".equals(command)) {
                receiveFile(serverIP, fileName);
            } else if ("put".equals(command)) {
                sendFile(serverIP, fileName);
            } else {
                System.err.println("Unknown command: " + command);
            }
        } catch (IOException e) {
            System.err.println("I/O Error: " + e.getMessage());
        }
    }

    private static void receiveFile(String serverIP, String fileName) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(3000); // 3 seconds for timeouts
        InetAddress serverAddress = InetAddress.getByName(serverIP);

        byte[] rrqPacket = createRequest(op_read_request, fileName);
        socket.send(new DatagramPacket(rrqPacket, rrqPacket.length, serverAddress, port));

        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            int expectedBlock = 1;
            while (true) {
                DatagramPacket incoming = new DatagramPacket(new byte[packet_size], packet_size);
                int attempts = 0;
                boolean gotData = false;
                while (!gotData && attempts < 4) { // retry attemptr
                    try {
                        socket.receive(incoming);
                        gotData = true;
                    } catch (SocketTimeoutException ex) {
                        attempts++;
                        socket.send(new DatagramPacket(rrqPacket, rrqPacket.length, serverAddress, port));
                    }
                }
                if (!gotData) break; // stop

                int opcode = getOpcode(incoming.getData());
                if (opcode == op_data) {
                    int blockNum = getBlockNumber(incoming.getData());
                    if (blockNum == expectedBlock) {
                        fos.write(incoming.getData(), 4, incoming.getLength() - 4);
                        sendAck(socket, incoming.getAddress(), incoming.getPort(), blockNum);
                        if (incoming.getLength() < packet_size) {
                            break;
                        }
                        expectedBlock++;
                    }
                } else if (opcode == op_error) {
                    printError(incoming.getData());
                    break;
                } else {
                    System.err.println("Unexpected opcode: " + opcode);
                }
            }
        } finally {
            socket.close();
        }
    }

    private static void sendFile(String serverIP, String fileName) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(3000);
        InetAddress serverAddress = InetAddress.getByName(serverIP);

        byte[] wrqPacket = createRequest(op_write_request, fileName);
        socket.send(new DatagramPacket(wrqPacket, wrqPacket.length, serverAddress, port));

        DatagramPacket ackPacket = new DatagramPacket(new byte[packet_size], packet_size);

        int attempts = 0;
        boolean gotAck = false;
        while (!gotAck && attempts < 4) {
            try {
                socket.receive(ackPacket);
                gotAck = true;
            } catch (SocketTimeoutException ex) {
                attempts++;
                socket.send(new DatagramPacket(wrqPacket, wrqPacket.length, serverAddress, port));
            }
        }
        if (!gotAck) {
            socket.close();
            return;
        }

        if (getOpcode(ackPacket.getData()) == op_error) {
            printError(ackPacket.getData());
            socket.close();
            return;
        }

        try (FileInputStream fis = new FileInputStream(fileName)) {
            int blockNumber = 1;
            byte[] buffer = new byte[512];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] dataPacket = new byte[4 + bytesRead];
                dataPacket[0] = 0;
                dataPacket[1] = (byte) op_data;
                dataPacket[2] = (byte) (blockNumber >> 8);
                dataPacket[3] = (byte) blockNumber;
                System.arraycopy(buffer, 0, dataPacket, 4, bytesRead);

                DatagramPacket outData = new DatagramPacket(dataPacket, dataPacket.length, ackPacket.getAddress(), ackPacket.getPort());
                socket.send(outData);

                boolean acked = false;
                int tries = 0;
                while (!acked && tries < 4) {
                    try {
                        socket.receive(ackPacket);
                        acked = true;
                    } catch (SocketTimeoutException e) {
                        tries++;
                        socket.send(outData);
                    }
                }
                if (!acked) break;

                if (getOpcode(ackPacket.getData()) == op_error) {
                    printError(ackPacket.getData());
                    break;
                }
                blockNumber++;
                if (bytesRead < 512) {
                    break;
                }
            }
        } finally {
            socket.close();
        }
    }

    private static byte[] createRequest(int opcode, String fileName) {
        byte[] fileBytes = fileName.getBytes();
        byte[] modeBytes = "octet".getBytes();
        byte[] packet = new byte[2 + fileBytes.length + 1 + modeBytes.length + 1];
        packet[0] = 0;
        packet[1] = (byte) opcode;
        System.arraycopy(fileBytes, 0, packet, 2, fileBytes.length);
        packet[2 + fileBytes.length] = 0;
        System.arraycopy(modeBytes, 0, packet, 3 + fileBytes.length, modeBytes.length);
        packet[packet.length - 1] = 0;
        return packet;
    }

    private static void sendAck(DatagramSocket socket, InetAddress address, int port, int blockNumber) throws IOException {
        byte[] ack = {0, (byte) op_acknowledgment, (byte)(blockNumber >> 8), (byte) blockNumber};
        socket.send(new DatagramPacket(ack, ack.length, address, port));
    }

    private static int getOpcode(byte[] data) {
        return ((data[0] & 0xff) << 8) | (data[1] & 0xff);
    }

    private static int getBlockNumber(byte[] data) {
        return ((data[2] & 0xff) << 8) | (data[3] & 0xff);
    }

    private static void printError(byte[] data) {
        int i = 4;
        StringBuilder sb = new StringBuilder();
        while (i < data.length && data[i] != 0) {
            sb.append((char) data[i++]);
        }
        System.err.println("Server error: " + sb);
    }
}
