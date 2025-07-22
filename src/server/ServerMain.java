package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class ServerMain {
       
       private static final String RESET ="\u001B[0m"; 
       private static final String RED ="\u001B[31m";
       private static final String GREEN ="\u001B[32m";
       private static final String PURPLE ="\u001B[35m";


       public static Map<String, ClientHandler> clients =new ConcurrentHashMap<>();

       public static void broadcastMessage(String message) {
              for(ClientHandler ch : clients.values()) {
                ch.sendMessage("Server: " + message);
              }
       }

       public static void disconnectClient(String username) {
              ClientHandler ch= clients.get(username);
              if(ch != null) {
                   ch.disconnect();
             }
       }

       public static List<String> getActiveClientUserNames() {
             return new ArrayList<>(clients.keySet());
       }

       public static void main(String[] args) throws InterruptedException {
           int port = 9999;
           ServerLogger.log(PURPLE + "Server initialized\nAcquiring port...\nport obtained : " + port + "..." + RESET);

           // shutdown hook for graceful client disconnect
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
}