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
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONArray;
import org.json.JSONObject;

public class clientMain {

    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[92m";
    private static final String RED = "\u001B[91m";

    private static void loadingBar() throws InterruptedException {
        int total = 50;
        for (int i = 0; i <= 100; i++) {
            int progress = (i * total) / 100;
            String bar = "[" + "0".repeat(progress) + " ".repeat(total - progress) + "]";
            System.out.print("\r" + GREEN + "Loading..." + bar + " " + i + "%" + RESET);
            Thread.sleep(50);
        }
        typeWritter("\n[*] Loading complete!\n", false);
    }

    private static void typeWritter(String message, boolean fast) {
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
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.print("\b");
            }
            System.out.print(realChar);
            System.out.flush();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("\n");
    }

    private static String encrypt(String data, String key) throws Exception {
        SecretKeySpec spec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        cipher.init(Cipher.ENCRYPT_MODE, spec, new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(data.getBytes("UTF-8"));
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    private static String decrypt(String encrypted, String key) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encrypted);
        byte[] iv = new byte[16];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        byte[] encryptedBytes = new byte[combined.length - iv.length];
        System.arraycopy(combined, iv.length, encryptedBytes, 0, encryptedBytes.length);
        SecretKeySpec spec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, spec, new IvParameterSpec(iv));
        byte[] original = cipher.doFinal(encryptedBytes);
        return new String(original, "UTF-8");
    }

    private static String getPreSharedKey(String recipient) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get("keys.txt"))))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2 && parts[0].equals(recipient)) {
                    return parts[1];
                }
            }
        }
        return "defaultkey1234567890123456";
    }

    public static void main(String[] args) throws InterruptedException {
        while (true) {
            String serverIp = waitForBeacon();
            if (serverIp != null) {
                startClient(serverIp);
                break;
            } else {
                typeWritter(RED + "[!] Beacon failed, retrying in 5 seconds..." + RESET, false);
                Thread.sleep(5000);
            }
        }
    }

    private static String waitForBeacon() {
        try (DatagramSocket socket = new DatagramSocket(8888)) {
            socket.setSoTimeout(5000);
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            typeWritter(GREEN + "[*] Listening for UDP beacon on port 8888..." + RESET, false);
            for (int i = 0; i < 3; i++) {
                try {
                    socket.receive(packet);
                    String beacon = new String(packet.getData(), 0, packet.getLength());
                    String senderIp = packet.getAddress().getHostAddress();
                    typeWritter(GREEN + "[*] Beacon received from " + senderIp + ": " + beacon + RESET, false);
                    return senderIp;
                } catch (SocketTimeoutException e) {
                    typeWritter(RED + "[!] Attempt " + (i + 1) + "/3 timed out." + RESET, false);
                }
            }
            return null;
        } catch (IOException e) {
            typeWritter(RED + "[!] Error receiving beacon: " + e.getMessage() + RESET, false);
            return null;
        }
    }

    private static void startClient(String host) throws InterruptedException {
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
                typeWritter(GREEN + "[*] Connecting to server at " + host + RESET, false);
                loadingBar();

                typeWritter(GREEN + "Enter username: " + RESET, false);
                String username = scanner.nextLine().trim();
                while (username.isEmpty() || username.contains(":")) {
                    typeWritter(RED + "[!] Username cannot be empty or contain ':'" + RESET, false);
                    username = scanner.nextLine().trim();
                }

                typeWritter(GREEN + "Enter password: " + RESET, false);
                String password = scanner.nextLine().trim();
                while (password.isEmpty()) {
                    typeWritter(RED + "[!] Password cannot be empty" + RESET, false);
                    password = scanner.nextLine().trim();
                }

                JSONObject login = new JSONObject();
                login.put("type", "login");
                login.put("username", username);
                login.put("password", password);
                typeWritter(GREEN + "[*] Logging in as " + username + RESET, false);
                out.println(login.toString());
                out.flush();

                String loginReply = in.readLine();
                if (loginReply == null) {
                    typeWritter(RED + "[!] No login response received" + RESET, false);
                    return;
                }
                System.out.println(GREEN + "[Server] " + loginReply + RESET);

                JSONObject beaconRequest = new JSONObject();
                beaconRequest.put("type", "beacon_request");
                out.println(beaconRequest.toString());
                out.flush();

                Thread listenerThread = new Thread(() -> {
                    try {
                        String reply;
                        while ((reply = in.readLine()) != null) {
                            typeWritter(GREEN + "[DEBUG] Received at " + System.currentTimeMillis() + ": " + reply + RESET, true);
                            JSONObject json = new JSONObject(reply);
                            String type = json.optString("type");

                            switch (type) {
                                case "broadcast" ->
                                    typeWritter("[Broadcast from " + json.optString("from") + "] " + json.optString("body"), true);
                                case "message" ->
                                    typeWritter("[Private from " + json.optString("from") + "] " + json.optString("body"), true);
                                case "info", "error" ->
                                    typeWritter("[" + type.toUpperCase() + "] " + json.optString("body"), true);
                                case "user_list" -> {
                                    JSONArray users = json.getJSONArray("users");
                                    typeWritter(GREEN + "Active users: " + users.toString() + RESET, true);
                                }
                                case "file_download" -> {
                                    String base64 = json.optString("content");
                                    String filename = json.optString("filename");
                                    byte[] bytes = Base64.getDecoder().decode(base64);
                                    try (FileOutputStream fos = new FileOutputStream(filename)) {
                                        fos.write(bytes);
                                        typeWritter(GREEN + "File downloaded: " + filename + RESET, true);
                                    } catch (IOException e) {
                                        typeWritter(RED + "[!] Download failed: " + e.getMessage() + RESET, true);
                                    }
                                }
                                case "encrypted_message" -> {
                                    try {
                                        String eString = json.optString("body");
                                        String sender = json.optString("from");
                                        String key = getPreSharedKey(sender);
                                        String decrypted = decrypt(eString, key);
                                        typeWritter("[Encrypted from " + sender + "] " + decrypted, true);
                                    } catch (Exception e) {
                                        typeWritter(RED + "[!] Decryption failed: " + e.getMessage() + RESET, true);
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
                                    typeWritter(GREEN + lb.toString() + RESET, true);
                                }
                                default -> typeWritter(RED + "[!] Unknown message type: " + type + RESET, true);
                            }
                        }
                    } catch (Exception e) {
                        typeWritter(RED + "[!] Listener stopped: " + e.getMessage() + RESET, false);
                    }
                });
                listenerThread.start();

                Map<String, String> helpCommands = new HashMap<>();
                helpCommands.put("/list_users", "List all active users");
                helpCommands.put("/msg <user> <message>", "Send a private message to a user");
                helpCommands.put("/broadcast <message>", "Send a message to all users");
                helpCommands.put("/encrypt <user> <message>", "Send an encrypted message to a user");
                helpCommands.put("/anon <message>", "Send an anonymous broadcast");
                helpCommands.put("/rank", "Show your credits and rank");
                helpCommands.put("/upload_challenge <filename> <flag>", "Upload a challenge file with a flag");
                helpCommands.put("/solve <filename> <flag>", "Submit a flag to solve a challenge");
                helpCommands.put("/leaderboard [page]", "Show the leaderboard (default page 1)");
                helpCommands.put("/help", "Show this help message");
                helpCommands.put("/logout", "Log out and disconnect");

                while (true) {
                    typeWritter(GREEN + "> " + RESET, false);
                    String msg = scanner.nextLine().trim();

                    if (msg.equalsIgnoreCase("exit") || msg.equalsIgnoreCase("/logout")) {
                        listenerThread.interrupt();
                        JSONObject logout = new JSONObject();
                        logout.put("type", "logout");
                        out.println(logout.toString());
                        out.flush();
                        break;
                    }
                    if (msg.trim().isEmpty()) {
                        typeWritter(RED + "[!] Message cannot be empty" + RESET, false);
                        continue;
                    }

                    if (msg.equalsIgnoreCase("/help")) {
                        StringBuilder help = new StringBuilder("Available commands:\n");
                        helpCommands.forEach((cmd, desc) -> help.append(cmd).append(": ").append(desc).append("\n"));
                        typeWritter(GREEN + help.toString() + RESET, true);
                        continue;
                    }

                    if (msg.startsWith("/")) {
                        String[] parts = msg.split("\\s+", 4);
                        JSONObject command = new JSONObject();

                        switch (parts[0].toLowerCase()) {
                            case "/list_users" -> command.put("type", "list_users");
                            case "/msg" -> {
                                if (parts.length < 3) {
                                    typeWritter(RED + "[!] Usage: /msg <user> <message>" + RESET, false);
                                    continue;
                                }
                                String recipient = parts[1];
                                if (recipient.equals(username)) {
                                    typeWritter(RED + "[!] Cannot message yourself" + RESET, false);
                                    continue;
                                }
                                String body = String.join(" ", parts, 2, parts.length);
                                command.put("type", "message");
                                command.put("to", recipient);
                                command.put("body", body.trim());
                            }
                            case "/leaderboard" -> {
                                command.put("type", "leaderboard");
                                command.put("page", parts.length > 1 && parts[1].matches("\\d+") ? Integer.parseInt(parts[1]) : 1);
                            }
                            case "/broadcast" -> {
                                if (parts.length < 2) {
                                    typeWritter(RED + "[!] Usage: /broadcast <message>" + RESET, false);
                                    continue;
                                }
                                String body = String.join(" ", parts, 1, parts.length);
                                command.put("type", "broadcast");
                                command.put("body", body.trim());
                            }
                            case "/encrypt" -> {
                                if (parts.length < 3) {
                                    typeWritter(RED + "[!] Usage: /encrypt <user> <message>" + RESET, false);
                                    continue;
                                }
                                try {
                                    String recipient = parts[1];
                                    String plainText = String.join(" ", parts, 2, parts.length);
                                    String key = getPreSharedKey(recipient);
                                    String encrypted = encrypt(plainText, key);
                                    command.put("type", "encrypted_message");
                                    command.put("to", recipient);
                                    command.put("body", encrypted);
                                } catch (Exception e) {
                                    typeWritter(RED + "[!] Encryption failed: " + e.getMessage() + RESET, false);
                                    continue;
                                }
                            }
                            case "/anon" -> {
                                if (parts.length < 2) {
                                    typeWritter(RED + "[!] Usage: /anon <message>" + RESET, false);
                                    continue;
                                }
                                String body = String.join(" ", parts, 1, parts.length);
                                command.put("type", "anon_broadcast");
                                command.put("body", body.trim());
                            }
                            case "/rank" -> command.put("type", "rank");
                            case "/upload_challenge" -> {
                                if (parts.length < 3) {
                                    typeWritter(RED + "[!] Usage: /upload_challenge <filename> <flag>" + RESET, false);
                                    continue;
                                }
                                try {
                                    String filename = parts[1];
                                    String flag = parts[2];
                                    byte[] fileBytes = Files.readAllBytes(Paths.get(filename));
                                    command.put("type", "upload_challenge");
                                    command.put("filename", filename);
                                    command.put("content", Base64.getEncoder().encodeToString(fileBytes));
                                    command.put("flag", flag);
                                } catch (IOException e) {
                                    typeWritter(RED + "[!] Upload failed: " + e.getMessage() + RESET, false);
                                    continue;
                                }
                            }
                            case "/solve" -> {
                                if (parts.length < 3) {
                                    typeWritter(RED + "[!] Usage: /solve <filename> <flag>" + RESET, false);
                                    continue;
                                }
                                command.put("type", "solve");
                                command.put("filename", parts[1]);
                                command.put("flag", parts[2]);
                            }
                            default -> {
                                typeWritter(RED + "[!] Unknown command: " + parts[0] + RESET, false);
                                continue;
                            }
                        }
                        out.println(command.toString());
                        out.flush();
                    } else {
                        JSONObject msgJson = new JSONObject();
                        msgJson.put("type", "broadcast");
                        msgJson.put("body", msg.trim());
                        out.println(msgJson.toString());
                        out.flush();
                    }
                }
                break;
            } catch (IOException e) {
                retryCount++;
                long backoff = INITIAL_BACKOFF_MS * (long) Math.pow(2, retryCount - 1);
                typeWritter(RED + "[!] Connection error: " + e.getMessage() + ". Retrying in "
                        + (backoff / 1000) + " seconds... (Attempt " + retryCount + "/" + MAX_RETRIES + ")" + RESET, false);
                Thread.sleep(backoff);
            }
        }

        if (retryCount >= MAX_RETRIES) {
            typeWritter(RED + "[!] Max retries reached. Exiting." + RESET, false);
        } else {
            typeWritter(GREEN + "[*] Connection closed." + RESET, false);
        }
    }
}