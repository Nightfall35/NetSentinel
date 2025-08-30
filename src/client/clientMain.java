package client;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Base64;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONObject;

public class clientMain {

    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[92m";
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
	System.out.println("\n");
    }
    public static void main(String[] args) {
        while (true) {
            String serverIp = waitForBeacon(); 
            if (serverIp != null) {
                startClient(serverIp);

                break;
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
        int retryCount = 0;
        final int MAX_RETRIES = 5;
        final long INITIAL_BACKOFF_MS = 1000; // 1 second

        while (retryCount < MAX_RETRIES) {
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
                typeWritter(GREEN + "Enter password: " + RESET);
                String password = scanner.nextLine().trim();
                while (password.isEmpty()) {
                    typeWritter(GREEN + "password cannot be empty " + RESET);
                    password = scanner.nextLine().trim();
                }
                while (username.isEmpty()) {
                    typeWritter(RED + "Username cannot be empty!" + RESET);
                    username = scanner.nextLine().trim();
                }

                JSONObject login = new JSONObject();
                login.put("type", "login");
                login.put("username", username);
                login.put("password", password);
                typeWritter(GREEN + "[*] Logging in as " + username + RESET);
                typeWritter(GREEN + "Sending login request..." + RESET);
                out.println(login.toString());

                String loginReply = in.readLine();
                if (loginReply == null) {
                    typeWritter(RED + "[!] No login response received." + RESET);
                    return;
                }
                System.out.println(GREEN + "[Server] " + loginReply + RESET);

                JSONObject beaconRequest = new JSONObject();
                beaconRequest.put("type", "beacon_request");
                out.println(beaconRequest.toString());

                String beaconReply = in.readLine();
                if (beaconReply != null) {
                    JSONObject beacon = new JSONObject(beaconReply);
                    if ("beacon".equals(beacon.optString("type"))) {
                        typeWritter(GREEN + "[!] Server Public IP: " + beacon.optString("public_ip") + RESET);
                    }
                }

                // Heartbeat thread
                Thread heartbeatThread = new Thread(() -> {
                    while (true) {
                        try {
                            Thread.sleep(10000); // Every 10 seconds
                            JSONObject ping = new JSONObject();
                            ping.put("type", "ping");
                            out.println(ping.toString());
                        } catch (InterruptedException e) {
                            break; // Stop on interrupt
                        }
                    }
                });
                heartbeatThread.start();

                // Main client loop for sending messages
                while (true) {
                    typeWritter(GREEN + "> " + RESET);
                    String msg = scanner.nextLine();

                    if (msg.equalsIgnoreCase("exit")) {
                        heartbeatThread.interrupt();
                        break;
                    }
                    if (msg.trim().isEmpty()) {
                        typeWritter(RED + "Message cannot be empty." + RESET);
                        continue;
                    }

                    if (msg.startsWith("/")) {
                        String[] parts = msg.split(" ", 3);
                        if (parts.length < 3 && !parts[0].equals("/list_users") && !parts[0].equals("/list_books") && !parts[0].equals("/my_rentals")) {
                            typeWritter(RED + "Invalid command format. Use /command args" + RESET);
                            continue;
                        }

                      
                    // Expects responses, tolerate timeouts
                    for (int i = 0; i < 2; i++) {
                        try {
                            socket.setSoTimeout(3000);
                            String reply = in.readLine();
                            if (reply == null) continue;

                            JSONObject json = new JSONObject(reply);
                            String type = json.optString("type");

                            switch (type) {
                                case "broadcast" -> typeWritter("[Broadcast from " + json.optString("from") + "] " + json.optString("body"));
                                case "message" -> typeWritter("[Private from " + json.optString("from") + "] " + json.optString("body"));
                                case "info", "error" -> typeWritter("[" + type.toUpperCase() + "] " + json.optString("body"));
                                case "user_list" -> {
                                    JSONArray users = json.getJSONArray("users");
                                    typeWritter(GREEN + "Active users: " + users.toString() + RESET);
                                }
                                case "book_list" -> {
                                    JSONArray books = json.getJSONArray("books");
                                    for (int j = 0; j < books.length(); j++) {
                                        JSONObject book = books.getJSONObject(j);
                                        typeWritter("ID: " + book.getInt("id") + ", Title: " + book.getString("title") + ", Author: " + book.optString("author") + ", Available: " + book.getBoolean("available"));
                                    }
                                }
                                case "my_rentals" -> {
                                    JSONArray rentals = json.getJSONArray("rentals");
                                    for (int j = 0; j < rentals.length(); j++) {
                                        JSONObject rental = rentals.getJSONObject(j);
                                        typeWritter("Book ID: " + rental.getInt("book_id") + ", Title: " + rental.getString("title") + ", Rented: " + rental.getString("rental_date") + ", Due: " + rental.getString("due_date"));
                                    }
                                }
                                case "file_download" -> {
                                    String base64 = json.optString("content");
                                    String filename = json.optString("filename");
                                    byte[] bytes = Base64.getDecoder().decode(base64);
                                    try (FileOutputStream fos = new FileOutputStream(filename)) {
                                        fos.write(bytes);
                                        typeWritter(GREEN + "Book downloaded: " + filename + RESET);
                                    } catch (IOException e) {
                                        typeWritter(RED + "Download failed: " + e.getMessage() + RESET);
                                    }
                                }
                                default -> typeWritter("[Server] " + reply);
                            }
                        } catch (SocketTimeoutException ste) {
                            //skip
                        }
                    }
                }

                // If loop exits normally, break out of retry
                break;
            } catch (IOException e) {
                retryCount++;
                long backoff = INITIAL_BACKOFF_MS * (long) Math.pow(2, retryCount - 1); // Exponential backoff
                typeWritter(RED + "[!] Connection error: " + e.getMessage() + ". Retrying in " + (backoff / 1000) + " seconds... (Attempt " + retryCount + "/" + MAX_RETRIES + ")" + RESET);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ignore) {}
            }
        }

        if (retryCount >= MAX_RETRIES) {
            typeWritter(RED + "[!] Max retries reached. Exiting." + RESET);
        } else {
            typeWritter(GREEN + "[*] Connection closed." + RESET);
        }
        System.exit(0);
    } // end of startClient method
}