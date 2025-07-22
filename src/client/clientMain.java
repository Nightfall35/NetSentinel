package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.Scanner;
import java.io.PrintWriter;
import java.net.Socket;


import org.json.JSONObject;
public class clientMain {

    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u002B[90m";
    private static final String RED = "\u001B[91m";

    private synchronized static void typeWritter(String message){
        for(int i=0;i<message.length();i++){
            System.out.print(message.charAt(i));
            try {
                Thread.sleep(60);   
            } catch (InterruptedException e) {
                System.out.println("failure on typewritter thread " + e.getMessage());
            } 
            
        }
    }
    public static void main(String[] args) {
        while (true) {
            String serverIp = waitForBeacon(); 
            if (serverIp != null) {
                startClient(serverIp);

                break; // Exit loop after successful connection
            } else {
                typeWritter(RED + "[!] Beacon failed, retrying in 5 seconds..." + RESET);
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


            typeWritter(GREEN + "[*] Listening for UDP beacon on port 8888..." + RESET);
            socket.receive(packet);

 
            String beacon = new String(packet.getData(), 0, packet.getLength());
            String senderIp = packet.getAddress().getHostAddress();

            typeWritter(GREEN + "[*] Beacon received from " + senderIp + ": " + beacon + RESET);

             return senderIp;
            }catch(SocketTimeoutException e) {
                 typeWritter(RED + "[!] Beacon timed out. " + RESET);
                 return null;
            }catch(IOException e) {
                typeWritter(RED+"[!] Error receiving beacon: " + e.getMessage() + RESET);
                 return null;
            }
    }

    private static void startClient(String host) {
        int port = 9999;

        try (
            Socket socket = new Socket(host, port);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in) // not closed , clossing it would close the System.in for whole jvm
        ) {
            typeWritter(GREEN + "[*] Connected to server at " + host + RESET);

            // Login
            typeWritter(GREEN + "Enter username: " + RESET);
            String username = scanner.nextLine().trim();
            while (username.isEmpty()) {
                typeWritter(RED + "Username cannot be empty!" + RESET);
                username = scanner.nextLine().trim();
            }

            JSONObject login = new JSONObject();
            login.put("type", "login");
            login.put("username", username);
            out.println(login.toString());

            String loginResp = in.readLine();
            if (loginResp == null) {
                typeWritter(RED + "[!] No login response received." + RESET);
                return;
            }
            System.out.println(GREEN + "[Server] " + loginResp + RESET);

            
            JSONObject beaconRequest = new JSONObject();
            beaconRequest.put("type", "beacon_request");
            out.println(beaconRequest.toString());

            String beaconReply = in.readLine();
            if (beaconReply != null) {
                JSONObject beacon = new JSONObject(beaconReply);
                if ("beacon".equals(beacon.optString("type"))) {
                    typeWritter(GREEN + "↪ Server Public IP: " + beacon.optString("public_ip") + RESET);
                }
            }
         
            Thread listenerThread = new Thread(() -> {
    try {

        try (
            Socket socket = new Socket(host, port);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in)
        ) {
            typeWritter(GREEN + "[*] Connected to server at " + host + RESET);

            // Login
            typeWritter(GREEN + "Enter username: " + RESET);
            String username = scanner.nextLine().trim();
            while (username.isEmpty()) {
                typeWritter(RED + "Username cannot be empty!" + RESET);
                username = scanner.nextLine().trim();
            }

            typeWritter(GREEN + "Enter password: " + RESET);
            String password = scanner.nextLine();
            while (password.isEmpty()) {
                typeWritter(RED + "Password cannot be empty!" + RESET);
                password = scanner.nextLine();
            }

            JSONObject login = new JSONObject();
            login.put("type", "login");
            login.put("username", username);
            login.put("password", password);
            out.println(login.toString());

            String loginResponse = in.readLine();
            if (loginResponse == null) {
                typeWritter(RED + "[!] No login response received." + RESET);
                return;
            }
            System.out.println(GREEN + "[Server] " + loginResponse + RESET);
                typeWritter(GREEN + "> " + RESET);
                String msg = scanner.nextLine();

                if (msg.equalsIgnoreCase("exit")) return;
                if (msg.trim().isEmpty()) {
                    typeWritter(RED + "Message cannot be empty." + RESET);
                    continue;
                }

               if(msg.startsWith("/")) {
                    String[] parts =msg.split(" ",3);
                    if(parts.length<3){
                        typeWritter(RED + "Invalid command format. Use /command args" + RESET);
                        continue;
                    }

                    JSONObject pm=new JSONObject();
                    pm.put("type", "message");
                    pm.put("to", parts[1]);
                    pm.put("body", parts[2]);
                    out.println(pm.toString());
                    } else if (msg.equalsIgnoreCase("/list_users")) {
                    JSONObject listUsers = new JSONObject();
                    listUsers.put("type", "list_users");
                    out.println(listUsers.toString());
                    } else {
                        JSONObject broadcast = new JSONObject();
                        broadcast.put("type", "broadcast");
                        broadcast.put("body", msg);
                        out.println(broadcast.toString());
                    }

                // Expects 2 responses (ping + broadcast), tolerate timeouts
                for (int i = 0; i < 2; i++) {
                    try {
                        socket.setSoTimeout(3000);
                        String reply = in.readLine();
                        if (reply == null) continue;

                        JSONObject json = new JSONObject(reply);
                        String type = json.optString("type");

                        switch (type) {
                            case "broadcast":
                                typeWritter("[Broadcast from " + json.optString("from") + "] " + json.optString("body"));
                                break;
                            case "message":
                                typeWritter("[Private from " + json.optString("from") + "] " + json.optString("body"));
                                break;
                            case "info":
                            case "error":
                                typeWritter("[" + type.toUpperCase() + "] " + json.optString("body"));
                                break;
                            default:
                                typeWritter("[Server] " + reply);
                        }
                    } catch (SocketTimeoutException ste) {
                        //skip
                    }
                }
            }

        } catch (IOException e) {
            typeWritter(RED + "[!] Connection to server failed: " + e.getMessage() + RESET);
        }
    }
}
