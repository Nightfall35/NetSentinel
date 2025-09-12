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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Random;
import java.util.Scanner;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONArray;
import org.json.JSONObject;

public class clientMain {
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[92m";
    private static final String RED = "\u001B[91m";

    private static void shadowCast() throws InterruptedException {
        int total = 50;
        for (int i = 0; i <= 100; i++) {
            if (Thread.interrupted()) throw new InterruptedException();
            int progress = (i * total) / 100;
            String bar = "[" + "0".repeat(progress) + " ".repeat(total - progress) + "]";
            System.out.print("\r" + GREEN + "Initializing..." + bar + " " + i + "%" + RESET);
            Thread.sleep(100);
        }
        glitchPrint(RESET + "\n[*] Initialization complete!\n", false);
    }

    private synchronized static void glitchPrint(String message, boolean fast) {
        if (fast) {
            System.out.println(message + "\n");
            return;
        }
        Random rand = new Random();
        for (int i = 0; i < message.length(); i++) {
            char realChar = message.charAt(i);
            for (int j = 0; j < 3; j++) {
                System.out.print((char) (rand.nextInt(94) + 33));
                System.out.flush();
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.print("\b");
            }
            System.out.print(realChar);
            System.out.flush();
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("\n");
    }

    private static String cipherLock(String data, String key) throws Exception {
        SecretKeySpec spec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, spec);
        byte[] encrypted = cipher.doFinal(data.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    private static String cipherUnlock(String encrypted, String key) throws Exception {
        SecretKeySpec spec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, spec);
        byte[] decoded = Base64.getDecoder().decode(encrypted);
        byte[] original = cipher.doFinal(decoded);
        return new String(original, "UTF-8");
    }

    public static void main(String[] args) throws InterruptedException {
        while (true) {
            String serverIp = detectPulse();
            if (serverIp != null) {
                initLink(serverIp);
                break;
            } else {
                glitchPrint(RED + "[!] Pulse detection failed, retrying in 5 seconds..." + RESET, false);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignore) {
                }
            }
        }
    }

    private static String detectPulse() {
        try (DatagramSocket socket = new DatagramSocket(8888)) {
            socket.setSoTimeout(10000);
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            glitchPrint(GREEN + "[*] Scanning for pulse on port 8888..." + RESET, false);
            socket.receive(packet);
            String pulse = new String(packet.getData(), 0, packet.getLength());
            String senderIp = packet.getAddress().getHostAddress();
            glitchPrint(GREEN + "[*] Pulse received from " + senderIp + ": " + pulse + RESET, false);
            return senderIp;
        } catch (SocketTimeoutException e) {
            glitchPrint(RED + "[!] Pulse timed out." + RESET, false);
            return null;
        } catch (IOException e) {
            glitchPrint(RED + "[!] Error receiving pulse: " + e.getMessage() + RESET, false);
            return null;
        }
    }

    private static void initLink(String host) throws InterruptedException {
        int port = 9999;
        int retryCount = 0;
        final int MAX_RETRIES = 5;
        final long INITIAL_BACKOFF_MS = 1000;

        while (retryCount < MAX_RETRIES) {
            try (
                Socket socket = new Socket(host, port);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                Scanner scanner = new Scanner(System.in)
            ) {
                socket.setSoTimeout(60000); // Increase timeout to 60s
                glitchPrint(GREEN + "[*] Connecting to server at " + host + RESET, false);
                shadowCast();

                glitchPrint(GREEN + "Enter username: " + RESET, false);
                String username = scanner.nextLine().trim();
                while (username.isEmpty()) {
                    glitchPrint(RED + "Username cannot be empty!" + RESET, false);
                    username = scanner.nextLine().trim();
                }

                glitchPrint(GREEN + "Enter password: " + RESET, false);
                String password = scanner.nextLine().trim();
                while (password.isEmpty()) {
                    glitchPrint(RED + "Password cannot be empty!" + RESET, false);
                    password = scanner.nextLine().trim();
                }

                JSONObject login = new JSONObject();
                login.put("type", "login");
                login.put("username", username);
                login.put("password", password);
                glitchPrint(GREEN + "[*] Logging in as " + username + RESET, false);
                glitchPrint(GREEN + "Sending login request..." + RESET, false);
                out.println(login.toString());
                out.flush();

                String loginReply = in.readLine();
                if (loginReply == null) {
                    glitchPrint(RED + "[!] No login response received." + RESET, false);
                    return;
                }
                JSONObject loginResponse = new JSONObject(loginReply);
                glitchPrint(GREEN + "[Server] " + loginResponse.getString("body") + RESET, false);

                JSONObject beaconRequest = new JSONObject();
                beaconRequest.put("type", "beacon_request");
                out.println(beaconRequest.toString());
                out.flush();

                Thread listenerThread = new Thread(() -> {
                    try {
                        String reply;
                        while ((reply = in.readLine()) != null) {
                            glitchPrint(GREEN + "[DEBUG] Received at " + System.currentTimeMillis() + ": " + reply + RESET, true);
                            JSONObject json = new JSONObject(reply);
                            String type = json.optString("type");

                            switch (type) {
                                case "broadcast" ->
                                    glitchPrint("[Broadcast from " + json.optString("from") + "] " + json.optString("body"), true);
                                case "message" ->
                                    glitchPrint("[Private from " + json.optString("from") + "] " + json.optString("body"), true);
                                case "info" ->
                                    glitchPrint("[INFO] " + json.optString("body"), true);
                                case "error" ->
                                    glitchPrint("[ERROR] " + json.optString("body"), true);
                                case "user_list" -> {
                                    JSONArray users = json.getJSONArray("users");
                                    glitchPrint(GREEN + "Active users: " + users.toString() + RESET, true);
                                }
                                case "file_download" -> {
                                    String base64 = json.optString("content");
                                    String filename = json.optString("filename");
                                    byte[] bytes = Base64.getDecoder().decode(base64);
                                    try (FileOutputStream fos = new FileOutputStream(filename)) {
                                        fos.write(bytes);
                                        glitchPrint(GREEN + "File downloaded: " + filename + RESET, true);
                                    } catch (IOException e) {
                                        glitchPrint(RED + "Download failed: " + e.getMessage() + RESET, true);
                                    }
                                }
                                case "encrypted_message" -> {
                                    try {
                                        String eString = json.optString("body");
                                        String key = json.optString("key");
                                        String decrypted = cipherUnlock(eString, key);
                                        glitchPrint("[Encrypted from " + json.optString("from") + "] " + decrypted, true);
                                    } catch (Exception e) {
                                        glitchPrint(RED + "Decryption failed: " + e.getMessage() + RESET, true);
                                    }
                                }
                                case "leaderboard" -> {
                                    JSONArray leaderboard = json.getJSONArray("leaderboard");
                                    int page = json.getInt("page");
                                    int totalPages = json.getInt("total_pages");
                                    StringBuilder lb = new StringBuilder("Leaderboard (Page " + page + "/" + totalPages + "):\n");
                                    for (int i = 0; i < leaderboard.length(); i++) {
                                        JSONObject entry = leaderboard.getJSONObject(i);
                                        lb.append(String.format("%d. %s (Cred: %s, Rank: %s)\n",
                                                i + 1 + (page - 1) * 5, entry.getString("username"), entry.getString("cred"), entry.getString("rank")));
                                    }
                                    glitchPrint(GREEN + lb.toString() + RESET, true);
                                }
                                case "room_message" ->
                                    glitchPrint("[Room " + json.getString("room_name") + " from " + json.getString("from") + "] " + json.getString("body"), true);
                                case "hint" ->
                                    glitchPrint(GREEN + "Hint for " + json.getString("challenge_id") + ": " + json.getString("hint") + RESET, true);
                                default ->
                                    glitchPrint("[ERROR] Unknown message type: " + type + RESET, true);
                            }
                        }
                    } catch (Exception e) {
                        glitchPrint(RED + "[!] Listener stopped: " + e.getMessage() + RESET, false);
                    }
                });
                listenerThread.start();

                while (true) {
                    glitchPrint(GREEN + "> " + RESET, false);
                    String msg = scanner.nextLine().trim();

                    if (msg.equalsIgnoreCase("exit")) {
                        listenerThread.interrupt();
                        break;
                    }
                    if (msg.trim().isEmpty()) {
                        glitchPrint(RED + "Message cannot be empty." + RESET, false);
                        continue;
                    }

                    if (msg.startsWith("/")) {
                        String[] parts = msg.split(" ", 4);
                        JSONObject command = new JSONObject();

                        switch (parts[0]) {
                            case "/list_users" -> command.put("type", "list_users");
                            case "/msg" -> {
                                if (parts.length < 3) {
                                    glitchPrint(RED + "Usage: /msg <user> <message>" + RESET, false);
                                    continue;
                                }
                                command.put("type", "message");
                                command.put("to", parts[1]);
                                command.put("body", parts[2]);
                            }
                            case "/leaderboard" -> {
                                command.put("type", "leaderboard");
                                if (parts.length > 1 && parts[1].matches("\\d+")) {
                                    command.put("page", Integer.parseInt(parts[1]));
                                } else {
                                    command.put("page", 1);
                                }
                            }
                            case "/broadcast" -> {
                                if (parts.length < 2) {
                                    glitchPrint(RED + "Usage: /broadcast <message>" + RESET, false);
                                    continue;
                                }
                                command.put("type", "broadcast");
                                command.put("body", parts[1]);
                            }
                            case "/encrypt" -> {
                                if (parts.length < 4) {
                                    glitchPrint(RED + "Usage: /encrypt <user> <key> <message>" + RESET, false);
                                    continue;
                                }
                                try {
                                    String recipient = parts[1];
                                    String key = parts[2];
                                    String plainText = parts[3];
                                    String encrypted = cipherLock(plainText, key);
                                    command.put("type", "encrypted_message");
                                    command.put("to", recipient);
                                    command.put("body", encrypted);
                                    command.put("key", key);
                                } catch (Exception e) {
                                    glitchPrint(RED + "Encryption failed: " + e.getMessage() + RESET, false);
                                    continue;
                                }
                            }
                            case "/anon" -> {
                                if (parts.length < 2) {
                                    glitchPrint(RED + "Usage: /anon <message>" + RESET, false);
                                    continue;
                                }
                                command.put("type", "anon_broadcast");
                                command.put("body", parts[1]);
                            }
                            case "/rank" ->
                                command.put("type", "rank");
                            case "/upload_challenge" -> {
                                if (parts.length < 3) {
                                    glitchPrint(RED + "Usage: /upload_challenge <filename> <flag> [hint]" + RESET, false);
                                    continue;
                                }
                                try {
                                    String filename = parts[1];
                                    String flag = parts[2];
                                    String hint = parts.length > 3 ? parts[3] : "No hint provided.";
                                    byte[] fileBytes = Files.readAllBytes(Paths.get(filename));
                                    command.put("type", "upload_challenge");
                                    command.put("filename", filename);
                                    command.put("content", Base64.getEncoder().encodeToString(fileBytes));
                                    command.put("flag", flag);
                                    command.put("hint", hint);
                                } catch (IOException e) {
                                    glitchPrint(RED + "Upload failed: " + e.getMessage() + RESET, false);
                                    continue;
                                }
                            }
                            case "/solve" -> {
                                if (parts.length < 3) {
                                    glitchPrint(RED + "Usage: /solve <filename> <flag>" + RESET, false);
                                    continue;
                                }
                                command.put("type", "solve");
                                command.put("filename", parts[1]);
                                command.put("flag", parts[2]);
                            }
                            case "/create_room" -> {
                                if (parts.length < 2) {
                                    glitchPrint(RED + "Usage: /create_room <room_name>" + RESET, false);
                                    continue;
                                }
                                command.put("type", "create_room");
                                command.put("room_name", parts[1]);
                            }
                            case "/join_room" -> {
                                if (parts.length < 2) {
                                    glitchPrint(RED + "Usage: /join_room <room_name>" + RESET, false);
                                    continue;
                                }
                                command.put("type", "join_room");
                                command.put("room_name", parts[1]);
                            }
                            case "/room_msg" -> {
                                if (parts.length < 3) {
                                    glitchPrint(RED + "Usage: /room_msg <room_name> <message>" + RESET, false);
                                    continue;
                                }
                                command.put("type", "room_msg");
                                command.put("room_name", parts[1]);
                                command.put("body", parts[2]);
                            }
                            case "/kick_room" -> {
                                if (parts.length < 3) {
                                    glitchPrint(RED + "Usage: /kick_room <room_name> <user>" + RESET, false);
                                    continue;
                                }
                                command.put("type", "kick_room");
                                command.put("room_name", parts[1]);
                                command.put("target_user", parts[2]);
                            }
                            case "/hint" -> {
                                if (parts.length < 2) {
                                    glitchPrint(RED + "Usage: /hint <filename>" + RESET, false);
                                    continue;
                                }
                                command.put("type", "hint");
                                command.put("challenge_id", parts[1]);
                            }
                            default -> {
                                glitchPrint(RED + "Unknown command: " + parts[0] + RESET, false);
                                continue;
                            }
                        }
                        out.println(command.toString());
                        out.flush();
                    } else {
                        JSONObject msgJson = new JSONObject();
                        msgJson.put("type", "broadcast");
                        msgJson.put("body", msg);
                        out.println(msgJson.toString());
                        out.flush();
                    }
                }
                break;
            } catch (IOException e) {
                retryCount++;
                long backoff = INITIAL_BACKOFF_MS * (long) Math.pow(2, retryCount - 1);
                glitchPrint(RED + "[!] Connection error: " + e.getMessage() + ". Retrying in "
                        + (backoff / 1000) + " seconds... (Attempt " + retryCount + "/" + MAX_RETRIES + ")" + RESET, false);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ignore) {
                }
            } catch (InterruptedException r) {
                Thread.currentThread().interrupt();
                glitchPrint(RED + "[!] Sleep interrupted: " + r.getMessage() + RESET, false);
            }
        }

        if (retryCount >= MAX_RETRIES) {
            glitchPrint(RED + "[!] Max retries reached. Exiting." + RESET, false);
        } else {
            glitchPrint(GREEN + "[*] Connection closed." + RESET, false);
        }
        System.exit(0);
    }
}