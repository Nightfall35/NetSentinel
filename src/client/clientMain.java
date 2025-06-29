package client;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import org.json.JSONObject;

public class clientMain {

    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[90m";
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
        int beaconPort = 8888;
        System.out.println(GREEN + "[*] Waiting for beacon on port " + beaconPort + "..." + RESET);

        try (ServerSocket beaconSocket = new ServerSocket(beaconPort)) {
            beaconSocket.setSoTimeout(30000); // wait 30 seconds max
            Socket beacon = beaconSocket.accept();
            BufferedReader in = new BufferedReader(new InputStreamReader(beacon.getInputStream()));
            String ip = in.readLine();
            beacon.close();
            if (ip == null || ip.isEmpty()) {
                System.out.println(RED + "[!] Empty beacon received!" + RESET);
                return null;
            }
            System.out.println(GREEN + "[+] Beacon received: " + ip + RESET);
            return ip.trim();
        } catch (SocketTimeoutException ste) {
            System.out.println(RED + "[!] Beacon timeout: no pulse received." + RESET);
        } catch (IOException e) {
            System.out.println(RED + "[!] Error receiving beacon: " + e.getMessage() + RESET);
        }
        return null;
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
