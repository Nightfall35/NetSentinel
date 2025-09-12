package server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;

public class ServerMain {
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String PURPLE = "\u001B[35m";

    public static Map<String, Set<ClientHandler>> cyberRooms = new ConcurrentHashMap<>();
    public static Map<String, String> roomOverseers = new ConcurrentHashMap<>();
    public static Map<String, String[]> userVault = new ConcurrentHashMap<>();
    public static Map<String, ClientHandler> activeNodes = new ConcurrentHashMap<>();
    public static final Object NODE_LOCK = new Object();

    public static void main(String[] args) throws InterruptedException {
        int port = 9999;
        ServerLogger.log(PURPLE + "Matrix initialized\nAcquiring port...\nPort secured: " + port + "..." + RESET);
        initVault(GREEN + "users.txt" + RESET);
        loadUserData("users.txt");

        new File("challenges").mkdirs();
        initVault("challenges.txt");
        initVault("solved_challenges.txt");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ServerLogger.log(PURPLE + "\n[!] Matrix shutting down. Disconnecting all nodes..." + RESET);
            for (ClientHandler ch : activeNodes.values()) {
                ch.terminate();
            }
        }));

        try (ServerSocket server = new ServerSocket(port)) {
            ServerLogger.log(PURPLE + "Matrix awaiting connections..." + RESET);
            while (true) {
                Socket client = server.accept();
                ServerLogger.log(GREEN + "Deploying pulse..." + RESET);
                Thread clientThread = new Thread(new ClientHandler(client));
                clientThread.setName(GREEN + "Node-" + client.getInetAddress() + RESET);
                clientThread.start();
            }
        } catch (IOException e) {
            ServerLogger.log(RED + "Matrix error: " + e.getMessage() + RESET);
        }
    }

    private static void loadUserData(String filename) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(":", 4);
                if (parts.length >= 2) {
                    String username = parts[0].trim();
                    String hash = parts[1].trim();
                    String cred = parts.length > 2 ? parts[2].trim() : "0";
                    String rank = parts.length > 3 ? parts[3].trim() : "Script Kiddie";
                    userVault.put(username, new String[]{hash, cred, rank});
                    count++;
                }
            }
            ServerLogger.log(GREEN + "Loaded " + count + " user(s) from vault." + RESET);
        } catch (IOException e) {
            ServerLogger.log(RED + "[!] Failed to load user data: " + e.getMessage() + RESET);
        }
    }

    public static String hashCode(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }

    private static void initVault(String filename) {
        try {
            File file = new File(filename);
            if (!file.exists()) {
                file.createNewFile();
                ServerLogger.log(GREEN + "Vault created: " + filename + RESET);
            } else {
                ServerLogger.log(GREEN + "Vault exists: " + filename + RESET);
            }
            if (file.exists()) {
                ServerLogger.log(GREEN + "Vault found: " + filename + RESET);
            } else {
                ServerLogger.log(RED + "[!] Vault not found: " + filename + RESET);
            }
        } catch (Exception e) {
            ServerLogger.log(RED + "[!] Error creating or checking vault: " + e.getMessage() + RESET);
        }
    }
}