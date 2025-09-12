package server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
        ServerLogger.log("Running clientHandler v2.0 (no ping/pong)");
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
                     if (currentTime - lastMessageTime < MIN_MESSAGE_INTERVAL_MS && !type.equals("login")) {
                         sendError("Message rate limit exceeded. Please wait before sending another message.");
                         continue;
                     }
                     lastMessageTime = currentTime;

                      switch (type) {
                         case "login":
                             handleLogin(message);
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
                    sendError("Invalid message format.");
                }
            }
        } catch (IOException e) {
            ServerLogger.log("Connection with client: " + client.getInetAddress() + " timed out due to inactivity");
        } finally {
            if (username != null) {
                ServerMain.clients.remove(username);
                ServerLogger.log("[" + username + "] disconnected");
            }
            try {
                client.close();
            } catch (IOException ignore) {}
        }
    }

    private void handleRank() {
        String[] userData = ServerMain.passwords.get(username);
        if (userData != null) {
            sendInfo("Cred: " + userData[1] + ", Rank: " + userData[2]);
        } else {
            sendError("User data not found.");
        }
    }

    private void updateCred(String user, int points) throws IOException {
        Map<String, String[]> users = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("users.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                users.put(parts[0], parts);
            }
        }
        String[] userData = users.get(user);
        int newCred = Integer.parseInt(userData[2]) + points;
        String newRank = newCred >= 1000 ? "Elite" : newCred >= 500 ? "Hacker" : "Script Kiddie";
        userData[2] = String.valueOf(newCred);
        userData[3] = newRank;
        users.put(user, userData);
        try (FileWriter fw = new FileWriter("users.txt")) {
            for (String[] data : users.values()) {
                fw.write(String.join(":", data) + "\n");
            }
        }
        ServerMain.passwords.put(user, new String[]{userData[1], userData[2], userData[3]});
        sendInfo("Cred updated: +" + points + ". New rank: " + newRank);
        ServerLogger.log("[" + user + "] Cred: " + newCred + ", Rank: " + newRank);
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
        String requestedUsername = message.optString("username", message.optString("from", null));
        String password = message.optString("password", null);

        if (requestedUsername == null || requestedUsername.trim().isEmpty()) {
            sendError("Username is required.");
            return;
        }
        if (password == null || password.isEmpty()) {
            sendError("Password is required.");
            return;
        }

        String[] userData = ServerMain.passwords.get(requestedUsername);
        String expectedHash = (userData != null) ? userData[0] : null;
        String providedHash = ServerMain.hashPassword(password);

        synchronized (ServerMain.CLIENTS_LOCK) {
            if (expectedHash == null) {
                try {
                    appendUserToFile(requestedUsername, providedHash);
                    ServerMain.passwords.put(requestedUsername, new String[]{providedHash, "0", "Script Kiddie"});
                    this.username = requestedUsername;
                    ServerMain.clients.put(username, this);
                    ServerLogger.log("[" + username + "] registered and logged in from " + client.getInetAddress());
                    sendInfo("Registration and login successful as " + username);
                } catch (IOException e) {
                    sendError("Failed to register new user: " + e.getMessage());
                }
            } else {
                if (!expectedHash.equals(providedHash)) {
                    sendError("Invalid password.");
                    return;
                }
                if (ServerMain.clients.containsKey(requestedUsername)) {
                    sendError("Username already taken.");
                    return;
                }
                this.username = requestedUsername;
                ServerMain.clients.put(username, this);
                ServerLogger.log("[" + username + "] logged in from " + client.getInetAddress());
                sendInfo("Login successful as " + username);
            }
        }
    }

    private void appendUserToFile(String username, String hashedPassword) throws IOException {
        try (FileWriter fw = new FileWriter("users.txt", true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(username + ":" + hashedPassword + ":0:Script Kiddie");
        }
    }

    private void handlePrivateMessage(JSONObject message) {
        String to = message.optString("to");
        String body = message.optString("body");

        ClientHandler recipient = ServerMain.clients.get(to);
        if (recipient != null) {
            JSONObject reply = new JSONObject();
            reply.put("type", "message");
            reply.put("from", username);
            reply.put("body", body);
            recipient.send(reply.toString());

            ServerLogger.log("private message from [" + username + "] to [" + to + "]: " + body);
        } else {
            sendError("user '" + to + "' not found.");
        }
    }

    private void handleBroadcast(JSONObject message) {
        String body = message.optString("body");

        JSONObject broadcastMsg = new JSONObject();
        broadcastMsg.put("type", "broadcast");
        broadcastMsg.put("from", username);
        broadcastMsg.put("body", body);

        for (ClientHandler handler : ServerMain.clients.values()) {
            if (!handler.username.equals(this.username)) {
                handler.send(broadcastMsg.toString());
            }
        }

        ServerLogger.log("Broadcast from [" + username + "] " + body);
    }
   
    private void handleEncryptedMessage(JSONObject message) {
        String to = message.optString("to");
        String body = message.optString("body");
        String key = message.optString("key");

        ClientHandler recipient = ServerMain.clients.get(to);
        if (recipient != null) {
            JSONObject reply = new JSONObject();
            reply.put("type", "encrypted_message");
            reply.put("from", username);
            reply.put("body", body);
            reply.put("key", key);
            recipient.send(reply.toString());

            ServerLogger.log("encrypted message from [" + username + "] to [" + to + "]: " + body);
        } else {
            sendError("user '" + to + "' not found.");
        }
    }

    private void handleAnonBroadcast(JSONObject message) {
        String body = message.optString("body");

        JSONObject broadcastMsg = new JSONObject();
        broadcastMsg.put("type","broadcast");
        broadcastMsg.put("from","Anonymous");
        broadcastMsg.put("body",body);

        synchronized (ServerMain.CLIENTS_LOCK) {
            for(ClientHandler handler : ServerMain.clients.values()) {
                if (!handler.username.equals(this.username)) {
                    handler.send(broadcastMsg.toString());
                }
            }

        }
        ServerLogger.log("Anonymous broadcast from [" + username + "] " + body);
    }
    
    private void handleChallengeUpload(JSONObject message) throws IOException {
        String filename = message.optString("filename");
        String content = message.optString("content");
        String flag = message.optString("flag");

        if(filename == null || filename.trim().isEmpty() || content ==null || content.trim().isEmpty() || flag == null || flag.trim().isEmpty()) {
            sendError("Filename,content , and flag cannot be empty.");
            return;
        }

        File challengesDir = new File("challenges");
        if(!challengesDir.exists() && !challengesDir.mkdirs()) {
            sendError("Failed to create challenges directory.");
            return;
        }

        if(!filename.matches("^[a-zA-Z0-9._-]+$") || filename.contains("..")) {
            sendError("Invalid filename. Only alphanumeric characters, dots, underscores, and hyphens are allowed.");
            return;
        }

        
        try (FileOutputStream fos = new FileOutputStream("challenges/" + filename)) {
           try{
                 fos.write(Base64.getDecoder().decode(content));
              } catch(IllegalArgumentException e) {
                 sendError("Content is not valid base64.");
                 return;
              }
        }

        String hashedFlag = ServerMain.hashPassword(flag);

        synchronized (ServerMain.CLIENTS_LOCK) {
            try (FileWriter fw = new FileWriter("challenges.txt", true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(filename + ":" + hashedFlag + ":" + username);
            }
        }

       try{ 
        updateCred(username, 50);
       }catch(IOException e) {
        sendError("Failed to update cred: " + e.getMessage());
        return;
       }
        ServerLogger.log("Challenge uploaded by [" + username + "]: " + filename);
        sendInfo("Challenge uploaded: " + filename);
    }

    private void handleChallengeSolve(JSONObject message) throws IOException {
        String filename = message.optString("filename");
        String submittedFlag = message.optString("flag");

        if(filename == null || filename.trim().isEmpty() || submittedFlag == null || submittedFlag.trim().isEmpty()) {
            sendError("Filename and flag cannot be empty.");
            return;
        }

        if(!filename.matches("^[a-zA-Z0-9._-]+$") || filename.contains("..")) {
            sendError("Invalid filename. Only alphanumeric characters, dots, underscores, and hyphens are allowed.");
            return;
        }

        if(hasSolvedChallenge(username, filename)) {
            sendError("You have already solved this challenge.");
            return;
        }

        String hashedSubmittedFlag = ServerMain.hashPassword(submittedFlag);

      synchronized(ServerMain.CLIENTS_LOCK) {
        try (BufferedReader reader = new BufferedReader(new FileReader("challenges.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");

                if(parts.length < 3) {
                    ServerLogger.log("Malformed line in challenges.txt: " + line);
                    continue;
                }
                if (parts[0].equals(filename) && parts[1].equals(hashedSubmittedFlag)) {
                    markChallengeSolved(username, filename);
                    try{
                       updateCred(username, 100);
                    }catch(IOException e) {
                        sendError("Failed to update cred: " + e.getMessage());
                        return;
                    }
                    sendInfo("Flag correct! Cred +100");
                    ServerLogger.log("[" + username + "] solved challenge: " + filename);
                    return;
                }
            }
        }
     }
        sendError("Incorrect flag or challenge not found.");
    }

    private boolean hasSolvedChallenge(String username, String filename) throws IOException {
        File solvedFile = new File("solved_challenges.txt");
        if(!solvedFile.exists()) {
            return false;
        }
        try(BufferedReader reader = new BufferedReader(new FileReader(solvedFile))) {
            String line;
            while((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if(parts.length == 2 && parts[0].equals(username) && parts[1].equals(filename)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleLeaderboard(JSONObject message) throws IOException {
        int page = message.optInt("page",1);
        if(page < 1) {
            sendError("Page number must be positive.");
            return;
        }
        List<String[]> sortedUsers = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("users.txt"))) {
            String line;
            while((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if(parts.length >= 4) {
                    sortedUsers.add(new String[]{parts[0], parts[2], parts[3]});
                }
            }
        }
        sortedUsers.sort((a,b) -> Integer.compare(Integer.parseInt(b[1]), Integer.parseInt(a[1])));
        int pageSize = 5;
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, sortedUsers.size());
        if (start >= sortedUsers.size()) {
            sendError("Page " + page + " is empty.");
            return;
        }
        JSONArray leaderboard = new JSONArray();
        for(int i = start; i < end; i++) {
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

    private void markChallengeSolved(String username, String filename) throws IOException {
        synchronized (ServerMain.CLIENTS_LOCK) {
            try(FileWriter fw = new FileWriter("solved_challenges.txt", true);
                PrintWriter pw = new PrintWriter(fw)) {
                pw.println(username + ":" + filename);
            }
        }
    }

    private void handleBeaconRequest() {
        try (BufferedReader reader = new BufferedReader(new FileReader("public_ip.txt"))) {
            String ip = reader.readLine().trim();
            JSONObject beacon = new JSONObject();
            beacon.put("type", "beacon");
            beacon.put("public_ip", ip);
            send(beacon.toString());
            ServerLogger.log("sent beacon to [" + username + "]: " + ip);
        } catch (IOException e) {
            sendError("Failed to read public ip. it is either missing or empty. run public ip batch file to create it");
        }
    }

    public void disconnect() {
        try {
            client.close();
            ServerMain.clients.remove(username);
            ServerLogger.log("[" + username + "] disconnected");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String message) {
        JSONObject response = new JSONObject();
        response.put("type", "server_message");
        response.put("message", message);
        out.println(response.toString());
        out.flush();
    }

    private void send(String json) {
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