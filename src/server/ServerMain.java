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

public class ServerMain {
       
       private static final String RESET ="\u001B[0m"; 
       private static final String RED ="\u001B[31m";
       private static final String GREEN ="\u001B[32m";
       private static final String PURPLE ="\u001B[35m";

       public static Map<String, Set<ClientHandler>> rooms = new ConcurrentHashMap<>();
       public static Map<String, String> roomAdmins = new ConcurrentHashMap<>();
       public static Map<String, String[]> passwords = new ConcurrentHashMap<>();
       public static Map<String, ClientHandler> clients =new ConcurrentHashMap<>();
       public static final Object CLIENTS_LOCK =new Object();

       public static void main(String[] args) throws InterruptedException {
           int port = 9999;
           ServerLogger.log(PURPLE + "Server initialized\nAcquiring port...\nport obtained : " + port + "..." + RESET);
           usersFile(GREEN + "users.txt" + RESET);
           loadUserCredentials("users.txt");

           new File("challenges").mkdirs();
           usersFile("challenges.txt");
           usersFile("solved_challenges.txt");

           Runtime.getRuntime().addShutdownHook(new Thread(() -> {
               ServerLogger.log(PURPLE + "\n[!] Server shutting down. Disconnecting all clients..." + RESET);
               for (ClientHandler ch : clients.values()) {
                   ch.disconnect();
               }
           }));

           try (ServerSocket server = new ServerSocket(port)) {
               ServerLogger.log(PURPLE + "Server awaiting connection..." + RESET);

               while (true) {
                   Socket client = server.accept();
                   ServerLogger.log(GREEN + "Deploying Beacon......" + RESET);

                   Thread clientThread = new Thread(new ClientHandler(client));
                   clientThread.setName(GREEN + "Client-" + client.getInetAddress() + RESET);
                   clientThread.start();
               }
           } catch (IOException e) {
               ServerLogger.log(RED + "Server error:" + e.getMessage() + RESET);
           }
       }

         private static void loadUserCredentials(String filename) {
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
                       passwords.put(username, new String[]{hash, cred, rank});
                       count++;
                   }
               }
               ServerLogger.log(GREEN + "Loaded " + count + " user(s) from credentials file." + RESET);
           } catch (IOException e) {
               ServerLogger.log(RED + "[!] Failed to load user credentials: " + e.getMessage() + RESET);
           }
       }

       public static String hashPassword(String password) {
           try {
               MessageDigest md = MessageDigest.getInstance("SHA-256");
               byte[] hash = md.digest(password.getBytes());
               StringBuilder sb = new StringBuilder();
               for (byte b : hash) {
                   sb.append(String.format("%02x", b));
               }
               return sb.toString();
           } catch (NoSuchAlgorithmException e) {
               throw new RuntimeException("SHA-256 not supported", e);
           }
        }

        private static void usersFile(String filename) {
            try {
                File file =new File(filename);
                if(!file.exists()) {
                    file.createNewFile();
                    ServerLogger.log(GREEN + "User credentials file created: " + filename + RESET);
                } else {
                    ServerLogger.log(GREEN + "User credentials file already exists: " + filename + RESET);
                }
                if(file.exists()) {
                    ServerLogger.log(GREEN + "User credentials file found: " + filename + RESET);
                } else {
                    ServerLogger.log(RED + "[!] User credentials file not found: " + filename + RESET);
                }
            } catch (Exception e) {
                ServerLogger.log(RED + "[!] Error creating or checking user credentials file: " + e.getMessage() + RESET);
            }
        }
}