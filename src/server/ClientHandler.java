package server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Base64;
import java.io.File;
import java.io.FileInputStream;

import org.json.JSONArray;
import org.json.JSONObject;

public class ClientHandler implements Runnable {
    private String username = "Anonymous";
    private final Socket client;
    private PrintWriter out;
    private Timer heartbeatTimer;

    public ClientHandler(Socket client) throws SocketException {
        this.client = client;
        client.setSoTimeout(60000);
    }

    @Override
    public void run() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter writer = new PrintWriter(client.getOutputStream(), true)
        ) {
            this.out = writer;
            heartbeatTimer = new Timer(true); // Daemon timer
            resetHeartbeatTimeout();
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
                    case "ping":
                        JSONObject pong = new JSONObject();
                        pong.put("type", "pong");
                        send(pong.toString());
                        resetHeartbeatTimeout(); // Reset on ping
                        ServerLogger.log("Ping received from [" + username + "] - replied with pong");
                        break;
                    case "beacon_request":
                        handleBeaconRequest();
                        break;
                    case "list_users":
                        handleListUsers();
                        break;
                    case "list_books":
                        handleListBooks();
                        break;
                    case "rent":
                        handleRent(message);
                        break;
                    case "return":
                        handleReturn(message);
                        break;
                    case "my_rentals":
                        handleMyRentals();
                        break;
                    case "download":
                        handleDownload(message);
                        break;
                    default:
                        sendError("Unknown message type: " + type);
                }
            }
        } catch (IOException e) {
            ServerLogger.log("Connection with client: " + client.getInetAddress() + " timed out due to inactivity");
        } finally {
            if (heartbeatTimer != null) {
                heartbeatTimer.cancel();
            }
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
            sendError("Failed to read public ip.it is either missing or empty. run public ip batch file to create it");
        }
    }

    private void handleListBooks() {
        try (Connection conn = ServerMain.getDBConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, title, author, available FROM books")) {
            JSONArray books = new JSONArray();
            while (rs.next()) {
                JSONObject book = new JSONObject();
                book.put("id", rs.getInt("id"));
                book.put("title", rs.getString("title"));
                book.put("author", rs.getString("author"));
                book.put("available", rs.getBoolean("available"));
                books.put(book);
            }
            JSONObject response = new JSONObject();
            response.put("type", "book_list");
            response.put("books", books);
            send(response.toString());
        } catch (SQLException e) {
            sendError("Database error: " + e.getMessage());
        }
    }

    private void handleRent(JSONObject message) {
        int bookId = message.optInt("book_id", -1);
        if (bookId == -1) {
            sendError("Invalid book ID.");
            return;
        }
        try (Connection conn = ServerMain.getDBConnection()) {
            conn.setAutoCommit(false);
            PreparedStatement checkStmt = conn.prepareStatement("SELECT available FROM books WHERE id = ?");
            checkStmt.setInt(1, bookId);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getBoolean("available")) {
                PreparedStatement updateBook = conn.prepareStatement("UPDATE books SET available = FALSE WHERE id = ?");
                updateBook.setInt(1, bookId);
                updateBook.executeUpdate();

                PreparedStatement insertRental = conn.prepareStatement("INSERT INTO rentals (user_username, book_id, rental_date, due_date) VALUES (?, ?, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 7 DAY))");
                insertRental.setString(1, username);
                insertRental.setInt(2, bookId);
                insertRental.executeUpdate();

                conn.commit();
                sendInfo("Book rented successfully. Due in 7 days.");
            } else {
                conn.rollback();
                sendError("Book not available.");
            }
        } catch (SQLException e) {
            sendError("Database error: " + e.getMessage());
        }
    }

    private void handleReturn(JSONObject message) {
        int bookId = message.optInt("book_id", -1);
        if (bookId == -1) {
            sendError("Invalid book ID.");
            return;
        }
        try (Connection conn = ServerMain.getDBConnection()) {
            conn.setAutoCommit(false);
            PreparedStatement checkRental = conn.prepareStatement("SELECT id FROM rentals WHERE user_username = ? AND book_id = ? AND returned = FALSE");
            checkRental.setString(1, username);
            checkRental.setInt(2, bookId);
            ResultSet rs = checkRental.executeQuery();
            if (rs.next()) {
                PreparedStatement updateRental = conn.prepareStatement("UPDATE rentals SET returned = TRUE WHERE id = ?");
                updateRental.setInt(1, rs.getInt("id"));
                updateRental.executeUpdate();

                PreparedStatement updateBook = conn.prepareStatement("UPDATE books SET available = TRUE WHERE id = ?");
                updateBook.setInt(1, bookId);
                updateBook.executeUpdate();

                conn.commit();
                sendInfo("Book returned successfully.");
            } else {
                conn.rollback();
                sendError("No active rental for this book.");
            }
        } catch (SQLException e) {
            sendError("Database error: " + e.getMessage());
        }
    }

    private void handleMyRentals() {
        try (Connection conn = ServerMain.getDBConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT b.id, b.title, r.rental_date, r.due_date FROM rentals r JOIN books b ON r.book_id = b.id WHERE r.user_username = ? AND r.returned = FALSE")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            JSONArray rentals = new JSONArray();
            while (rs.next()) {
                JSONObject rental = new JSONObject();
                rental.put("book_id", rs.getInt("id"));
                rental.put("title", rs.getString("title"));
                rental.put("rental_date", rs.getString("rental_date"));
                rental.put("due_date", rs.getString("due_date"));
                rentals.put(rental);
            }
            JSONObject response = new JSONObject();
            response.put("type", "my_rentals");
            response.put("rentals", rentals);
            send(response.toString());
        } catch (SQLException e) {
            sendError("Database error: " + e.getMessage());
        }
    }

    private void handleDownload(JSONObject message) {
        int bookId = message.optInt("book_id", -1);
        if (bookId == -1) {
            sendError("Invalid book ID.");
            return;
        }
        try (Connection conn = ServerMain.getDBConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT file_path FROM books b JOIN rentals r ON b.id = r.book_id WHERE b.id = ? AND r.user_username = ? AND r.returned = FALSE")) {
            stmt.setInt(1, bookId);
            stmt.setString(2, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String filePath = rs.getString("file_path");
                File file = new File(filePath);
                if (file.exists()) {
                    byte[] fileBytes = new byte[(int) file.length()];
                    try (FileInputStream fis = new FileInputStream(file)) {
                        fis.read(fileBytes);
                    }
                    String base64File = Base64.getEncoder().encodeToString(fileBytes);
                    JSONObject response = new JSONObject();
                    response.put("type", "file_download");
                    response.put("book_id", bookId);
                    response.put("content", base64File);
                    response.put("filename", file.getName());
                    send(response.toString());
                } else {
                    sendError("File not found on server.");
                }
            } else {
                sendError("You haven't rented this book or it's already returned.");
            }
        } catch (SQLException | IOException e) {
            sendError("Error: " + e.getMessage());
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

    private void resetHeartbeatTimeout() {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
        }
        heartbeatTimer = new Timer(true);
        heartbeatTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                ServerLogger.log("Heartbeat timeout for [" + username + "] - disconnecting");
                disconnect();
            }
        }, 30000); // 30-second timeout
    }
}