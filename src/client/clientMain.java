package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Scanner;

public class clientMain {

    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u002B[90m";
    private static final String RED = "\u001B[91m";

    public static void main(String[] args) {
        while (true) {
            String serverIp = waitForBeacon(); 
            if (serverIp != null) {
                startClient(serverIp);
                break; // Exit loop after successful connection
            } else {
                System.out.println(RED + "[!] Beacon failed, retrying in 5 seconds..." + RESET);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignore) {}
            }
        }
    }

    private static String waitForBeacon() {
            try(DatagramSocket socket = new DatagramSocket(8888)){
            socket.setSoTimeout(10000);
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer , buffer.length);


            System.out.println(GREEN + "[*] Listening for UDP beacon on port 8888..." + RESET);
            socket.receive(packet);

 
            String beacon = new String(packet.getData(), 0, packet.getLength());
            String senderIp = packet.getAddress().getHostAddress();

            System.out.println(GREEN + "[*] Beacon received from " + senderIp + ": " + beacon + RESET);

             return senderIp;
            }catch(SocketTimeoutException e) {
                 System.out.println(RED + "[!] Beacon timed out. " + RESET);
                 return null;
            }catch(IOException e) {
                 System.out.println(RED+"[!] Error receiving beacon: " + e.getMessage() + RESET);
                 return null;
            }
    }

    private static void startClient(String host) {
        int port = 9999;

        try (
            Socket socket = new Socket(host, port);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in)
        ) {
            System.out.println(GREEN + "[*] Connected to server at " + host + RESET);

            // Login
            System.out.print(GREEN + "Enter username: " + RESET);
            String username = scanner.nextLine().trim();
            while (username.isEmpty()) {
                System.out.println(RED + "Username cannot be empty!" + RESET);
                username = scanner.nextLine().trim();
            }

            JSONObject login = new JSONObject();
            login.put("type", "login");
            login.put("username", username);
            out.println(login.toString());

            String loginResponse = in.readLine();
            if (loginResponse == null) {
                System.out.println(RED + "[!] No login response received." + RESET);
                return;
            }
            System.out.println(GREEN + "[Server] " + loginResponse + RESET);

            // Optional: ask for beacon confirmation
            JSONObject beaconRequest = new JSONObject();
            beaconRequest.put("type", "beacon_request");
            out.println(beaconRequest.toString());

            String beaconReply = in.readLine();
            if (beaconReply != null) {
                JSONObject beacon = new JSONObject(beaconReply);
                if ("beacon".equals(beacon.optString("type"))) {
                    System.out.println(GREEN + "↪ Server Public IP: " + beacon.optString("public_ip") + RESET);
                }
            }

            // Message loop
            while (true) {
                System.out.print(GREEN + "> " + RESET);
                String msg = scanner.nextLine();

                if (msg.equalsIgnoreCase("exit")) break;
                if (msg.trim().isEmpty()) {
                    System.out.println(RED + "Message cannot be empty." + RESET);
                    continue;
                }

                // Ping
                JSONObject ping = new JSONObject();
                ping.put("type", "ping");
                out.println(ping.toString());

                // Send broadcast
                JSONObject broadcast = new JSONObject();
                broadcast.put("type", "broadcast");
                broadcast.put("body", msg);
                out.println(broadcast.toString());

                // Expect 2 responses (ping + broadcast), tolerate timeouts
                for (int i = 0; i < 2; i++) {
                    try {
                        socket.setSoTimeout(3000);
                        String reply = in.readLine();
                        if (reply == null) continue;

                        JSONObject json = new JSONObject(reply);
                        String type = json.optString("type");

                        switch (type) {
                            case "broadcast":
                                System.out.println("[Broadcast from " + json.optString("from") + "] " + json.optString("body"));
                                break;
                            case "message":
                                System.out.println("[Private from " + json.optString("from") + "] " + json.optString("body"));
                                break;
                            case "info":
                            case "error":
                                System.out.println("[" + type.toUpperCase() + "] " + json.optString("body"));
                                break;
                            default:
                                System.out.println("[Server] " + reply);
                        }
                    } catch (SocketTimeoutException ste) {
                        // Skip if server doesn't send a reply
                    }
                }
            }

        } catch (IOException e) {
            System.out.println(RED + "[!] Connection to server failed: " + e.getMessage() + RESET);
        }
    }
}
