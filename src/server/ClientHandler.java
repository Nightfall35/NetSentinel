package server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;

import org.json.JSONArray;
import org.json.JSONObject;

public class ClientHandler implements Runnable {
    private String username = "Anonymous";
    private final Socket client;
    private PrintWriter out;

    public ClientHandler(Socket client) throws SocketException {
        this.client = client;
        client.setSoTimeout(6000000);
    }

    @Override
    public void run() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter writer = new PrintWriter(client.getOutputStream(), true)
        ) {
            this.out = writer;
            String input;

            while ((input = in.readLine()) != null) {
                JSONObject message = new JSONObject(input);
                String type = message.getString("type");

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
                    default:
                        sendError("Unknown message type: " + type);
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

        String expectedHash = ServerMain.passwords.get(requestedUsername);
        String providedHash = ServerMain.hashPassword(password);

        synchronized (ServerMain.CLIENTS_LOCK) {
            if (expectedHash == null) {
                // User does not exist - register new user
                try {
                    appendUserToFile(requestedUsername, providedHash);
                    ServerMain.passwords.put(requestedUsername, providedHash);
                    this.username = requestedUsername;
                    ServerMain.clients.put(username, this);
                    ServerLogger.log("[" + username + "] registered and logged in from " + client.getInetAddress());
                    sendInfo("Registration and login successful as " + username);
                } catch (IOException e) {
                    sendError("Failed to register new user: " + e.getMessage());
                }
            } else {
                // User exists - check password
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
            pw.println(username + ":" + hashedPassword);
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

    // Utility Methods below

    private void send(String json) {
        out.println(json);
        out.flush(); // Ensure immediate sending
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