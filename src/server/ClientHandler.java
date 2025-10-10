package server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Base64;

public class ClientHandler implements Runnable {
    private String username = "Anonymous";
    private final Socket client;
    private PrintWriter out;
    private long lastMessageTime = 0;
    private static final long MIN_MESSAGE_INTERVAL_MS = 1000;

    public ClientHandler(Socket client) throws SocketException {
        this.client = client;
        client.setSoTimeout(30000);
    }

    @Override
    public void run() {
        ServerLogger.log("Running clientHandler v2.1");
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter writer = new PrintWriter(client.getOutputStream(), true)
        ) {
            this.out = writer;
            String input;

            while ((input = in.readLine()) != null) {
                ServerLogger.log("Received from [" + username + "]: " + input);
                try {
                    JSONObject message = new JSONObject(input);
                    String type = message.getString("type");

                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastMessageTime < MIN_MESSAGE_INTERVAL_MS && !type.equals("login") && !type.equals("logout")) {
                        sendError("Message rate limit exceeded. Please wait.");
                        continue;
                    }
                    lastMessageTime = currentTime;

                    switch (type) {
                        case "login":
                            handleLogin(message);
                            break;
                        case "logout":
                            handleLogout();
                            break;
                        case "message":
                            handlePrivateMessage(message);
                            break;
                        case "broadcast":
                            handleBroadcast(message);
                            break;
                        case "beacon_request":
                            handleBeaconRequest();
                            break;
                        case "list_users":
                            handleListUsers();
                            break;
                        case "encrypted_message":
                            handleEncryptedMessage(message);
                            break;
                        case "anon_broadcast":
                            handleAnonBroadcast(message);
                            break;
                        case "rank":
                            handleRank();
                            break;
                        case "upload_challenge":
                            handleChallengeUpload(message);
                            break;
                        case "solve":
                            handleChallengeSolve(message);
                            break;
                        case "leaderboard":
                            handleLeaderboard(message);
                            break;
                        default:
                            sendError("Unknown message type: " + type);
                    }
                } catch (org.json.JSONException e) {
                    sendError("Invalid message format: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            ServerLogger.log("Connection with [" + username + "] timed out: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    private void handleRank() {
        String[] userData = ServerMain.passwords.get(username);
        if (userData != null) {
            sendInfo("Cred: " + userData[1] + ", Rank: " + userData[2]);
        } else {
            sendError("User data not found");
        }
    }

    private void updateCred(String user, int points) throws IOException {
        synchronized (ServerMain.CLIENTS_LOCK) {
            String[] userData = ServerMain.passwords.get(user);
            int newCred = Integer.parseInt(userData[1]) + points;
            String newRank = newCred >= 1000 ? "Elite" : newCred >= 500 ? "Hacker" : "Script Kiddie";
            userData[1] = String.valueOf(newCred);
            userData[2] = newRank;
            ServerMain.passwords.put(user, userData);
            try (FileWriter fw = new FileWriter("users.txt")) {
                for (Map.Entry<String, String[]> entry : ServerMain.passwords.entrySet()) {
                    String[] data = entry.getValue();
                    fw.write(entry.getKey() + ":" + String.join(":", data) + "\n");
                }
            }
            sendInfo("Cred updated: +" + points + ". New rank: " + newRank);
            ServerLogger.log("[" + user + "] Cred: " + newCred + ", Rank: " + newRank);
        }
    }

    private void handleListUsers() {
        JSONArray userList = new JSONArray(ServerMain.clients.keySet());
        JSONObject response = new JSONObject();
        response.put("type", "user_list");
        response.put("users", userList);
        send(response.toString());
        ServerLogger.log("Sent user list to [" + username + "]");
    }

    private void handleLogin(JSONObject message) {
        String requestedUsername = message.optString("username");
        String password = message.optString("password");

        if (requestedUsername == null || requestedUsername.trim().isEmpty() || requestedUsername.contains(":")) {
            sendError("Invalid username: must not be empty or contain ':'");
            return;
        }
        if (password == null || password.isEmpty()) {
            sendError("Password is required");
            return;
        }

        String[] userData = ServerMain.passwords.get(requestedUsername);
        String expectedHash = (userData != null) ? userData[0] : null;
        String providedHash = ServerMain.hashPassword(password);

        synchronized (ServerMain.CLIENTS_LOCK) {
            if (expectedHash == null) {
                try {
                    ServerMain.passwords.put(requestedUsername, new String[]{providedHash, "0", "Script Kiddie"});
                    try (FileWriter fw = new FileWriter("users.txt", true)) {
                        fw.write(requestedUsername + ":" + providedHash + ":0:Script Kiddie\n");
                    }
                    this.username = requestedUsername;
                    ServerMain.clients.put(username, this);
                    ServerLogger.log("[" + username + "] registered and logged in from " + client.getInetAddress());
                    sendInfo("Registration and login successful as " + username);
                } catch (IOException e) {
                    sendError("Failed to register new user: " + e.getMessage());
                }
            } else {
                if (!expectedHash.equals(providedHash)) {
                    sendError("Invalid password");
                    return;
                }
                if (ServerMain.clients.containsKey(requestedUsername)) {
                    sendError("Username already taken");
                    return;
                }
                this.username = requestedUsername;
                ServerMain.clients.put(username, this);
                ServerLogger.log("[" + username + "] logged in from " + client.getInetAddress());
                sendInfo("Login successful as " + username);
            }
        }
    }

    private void handleLogout() {
        disconnect();
    }

    private void handlePrivateMessage(JSONObject message) {
        String to = message.optString("to");
        String body = message.optString("body");
        if (to.isEmpty() || body.isEmpty()) {
            sendError("Recipient and message body cannot be empty");
            return;
        }
        ClientHandler recipient = ServerMain.clients.get(to);
        if (recipient != null) {
            JSONObject reply = new JSONObject();
            reply.put("type", "message");
            reply.put("from", username);
            reply.put("body", body);
            recipient.send(reply.toString());
            ServerLogger.log("Private message from [" + username + "] to [" + to + "]: " + body);
        } else {
            sendError("User '" + to + "' not found");
        }
    }

    private void handleBroadcast(JSONObject message) {
        String body = message.optString("body");
        if (body.isEmpty()) {
            sendError("Broadcast message cannot be empty");
            return;
        }
        JSONObject broadcastMsg = new JSONObject();
        broadcastMsg.put("type", "broadcast");
        broadcastMsg.put("from", username);
        broadcastMsg.put("body", body);
        synchronized (ServerMain.CLIENTS_LOCK) {
            for (ClientHandler handler : ServerMain.clients.values()) {
                if (!handler.username.equals(this.username)) {
                    handler.send(broadcastMsg.toString());
                }
            }
        }
        ServerLogger.log("Broadcast from [" + username + "]: " + body);
    }

    private void handleEncryptedMessage(JSONObject message) {
        String to = message.optString("to");
        String body = message.optString("body");
        if (to.isEmpty() || body.isEmpty()) {
            sendError("Recipient and message body cannot be empty");
            return;
        }
        ClientHandler recipient = ServerMain.clients.get(to);
        if (recipient != null) {
            JSONObject reply = new JSONObject();
            reply.put("type", "encrypted_message");
            reply.put("from", username);
            reply.put("body", body);
            recipient.send(reply.toString());
            ServerLogger.log("Encrypted message from [" + username + "] to [" + to + "]");
        } else {
            sendError("User '" + to + "' not found");
        }
    }

    private void handleAnonBroadcast(JSONObject message) {
        String body = message.optString("body");
        if (body.isEmpty()) {
            sendError("Anonymous broadcast message cannot be empty");
            return;
        }
        JSONObject broadcastMsg = new JSONObject();
        broadcastMsg.put("type", "broadcast");
        broadcastMsg.put("from", "Anonymous");
        broadcastMsg.put("body", body);
        synchronized (ServerMain.CLIENTS_LOCK) {
            for (ClientHandler handler : ServerMain.clients.values()) {
                if (!handler.username.equals(this.username)) {
                    handler.send(broadcastMsg.toString());
                }
            }
        }
        ServerLogger.log("Anonymous broadcast: " + body);
    }

    private void handleChallengeUpload(JSONObject message) throws IOException {
        String filename = message.optString("filename");
        String content = message.optString("content");
        String flag = message.optString("flag");
        if (filename == null || filename.trim().isEmpty() || content == null || content.trim().isEmpty() || flag == null || flag.trim().isEmpty()) {
            sendError("Filename, content, and flag cannot be empty");
            return;
        }
        if (!filename.matches("^[a-zA-Z0-9._-]+$") || filename.contains("..")) {
            sendError("Invalid filename: only alphanumeric, dots, underscores, and hyphens allowed");
            return;
        }
        File challengesDir = new File("challenges");
        if (!challengesDir.exists() && !challengesDir.mkdirs()) {
            sendError("Failed to create challenges directory");
            return;
        }
        if (new File("challenges/" + filename).exists()) {
            sendError("Challenge file already exists");
            return;
        }
        try (FileOutputStream fos = new FileOutputStream("challenges/" + filename)) {
            fos.write(Base64.getDecoder().decode(content));
        } catch (IllegalArgumentException e) {
            sendError("Content is not valid Base64");
            return;
        }
        String hashedFlag = ServerMain.hashPassword(flag);
        synchronized (ServerMain.CLIENTS_LOCK) {
            ServerMain.challenges.put(filename, new String[]{hashedFlag, username});
            try (FileWriter fw = new FileWriter("challenges.txt", true)) {
                fw.write(filename + ":" + hashedFlag + ":" + username + "\n");
            }
            updateCred(username, 50);
        }
        sendInfo("Challenge uploaded: " + filename);
        ServerLogger.log("Challenge uploaded by [" + username + "]: " + filename);
    }

    private void handleChallengeSolve(JSONObject message) throws IOException {
        String filename = message.optString("filename");
        String submittedFlag = message.optString("flag");
        if (filename == null || filename.trim().isEmpty() || submittedFlag == null || submittedFlag.trim().isEmpty()) {
            sendError("Filename and flag cannot be empty");
            return;
        }
        if (!filename.matches("^[a-zA-Z0-9._-]+$") || filename.contains("..")) {
            sendError("Invalid filename: only alphanumeric, dots, underscores, and hyphens allowed");
            return;
        }
        if (ServerMain.solvedChallenges.getOrDefault(username, ConcurrentHashMap.newKeySet()).contains(filename)) {
            sendError("You have already solved this challenge");
            return;
        }
        String[] challengeData = ServerMain.challenges.get(filename);
        if (challengeData == null) {
            sendError("Challenge not found");
            return;
        }
        String hashedSubmittedFlag = ServerMain.hashPassword(submittedFlag);
        if (challengeData[0].equals(hashedSubmittedFlag)) {
            synchronized (ServerMain.CLIENTS_LOCK) {
                ServerMain.solvedChallenges.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet()).add(filename);
                try (FileWriter fw = new FileWriter("solved_challenges.txt", true)) {
                    fw.write(username + ":" + filename + "\n");
                }
                updateCred(username, 100);
                sendInfo("Flag correct! Cred +100");
                ServerLogger.log("[" + username + "] solved challenge: " + filename);
                try {
                    byte[] fileBytes = Files.readAllBytes(Paths.get("challenges/" + filename));
                    JSONObject download = new JSONObject();
                    download.put("type", "file_download");
                    download.put("filename", filename);
                    download.put("content", Base64.getEncoder().encodeToString(fileBytes));
                    send(download.toString());
                } catch (IOException e) {
                    sendError("Failed to send challenge file: " + e.getMessage());
                }
            }
        } else {
            sendError("Incorrect flag");
        }
    }

    private void handleLeaderboard(JSONObject message) {
        int page = message.optInt("page", 1);
        if (page < 1) {
            sendError("Page number must be positive");
            return;
        }
        List<String[]> sortedUsers = new ArrayList<>();
        synchronized (ServerMain.CLIENTS_LOCK) {
            for (Map.Entry<String, String[]> entry : ServerMain.passwords.entrySet()) {
                sortedUsers.add(new String[]{entry.getKey(), entry.getValue()[1], entry.getValue()[2]});
            }
        }
        sortedUsers.sort((a, b) -> Integer.compare(Integer.parseInt(b[1]), Integer.parseInt(a[1])));
        int pageSize = 5;
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, sortedUsers.size());
        if (start >= sortedUsers.size()) {
            sendError("Page " + page + " is empty");
            return;
        }
        JSONArray leaderboard = new JSONArray();
        for (int i = start; i < end; i++) {
            JSONObject entry = new JSONObject();
            entry.put("username", sortedUsers.get(i)[0]);
            entry.put("cred", sortedUsers.get(i)[1]);
            entry.put("rank", sortedUsers.get(i)[2]);
            leaderboard.put(entry);
        }
        JSONObject response = new JSONObject();
        response.put("type", "leaderboard");
        response.put("leaderboard", leaderboard);
        response.put("page", page);
        response.put("total_pages", (int) Math.ceil((double) sortedUsers.size() / pageSize));
        send(response.toString());
        ServerLogger.log("Sent leaderboard page " + page + " to [" + username + "]");
    }

    private void handleBeaconRequest() {
        try (BufferedReader reader = new BufferedReader(new FileReader("public_ip.txt"))) {
            String ip = reader.readLine().trim();
            JSONObject beacon = new JSONObject();
            beacon.put("type", "beacon");
            beacon.put("public_ip", ip);
            send(beacon.toString());
            ServerLogger.log("Sent beacon to [" + username + "]: " + ip);
        } catch (IOException e) {
            sendError("Failed to read public IP: " + e.getMessage());
        }
    }

    public void disconnect() {
        try {
            synchronized (ServerMain.CLIENTS_LOCK) {
                ServerMain.clients.remove(username);
                JSONObject disconnectMsg = new JSONObject();
                disconnectMsg.put("type", "broadcast");
                disconnectMsg.put("from", "Server");
                disconnectMsg.put("body", username + " has disconnected");
                for (ClientHandler handler : ServerMain.clients.values()) {
                    handler.send(disconnectMsg.toString());
                }
            }
            client.close();
            ServerLogger.log("[" + username + "] disconnected");
        } catch (IOException e) {
            ServerLogger.log("[" + username + "] disconnect error: " + e.getMessage());
        }
    }

    public void send(String json) {
        out.println(json);
        out.flush();
    }

    private void sendError(String msg) {
        sendJSON("error", msg);
    }

    private void sendInfo(String msg) {
        sendJSON("info", msg);
    }

    private void sendJSON(String type, String body) {
        JSONObject response = new JSONObject();
        response.put("type", type);
        if (body != null) response.put("body", body);
        send(response.toString());
    }
}