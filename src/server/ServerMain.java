package server;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;


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
	      int port =9999;
	      ServerLogger.log(PURPLE+"Server initialized\nAcquiring port...\nport obtained : "+port+"..."+RESET);
	      
	      try(ServerSocket server = new ServerSocket(port)) {
                  ServerLogger.log(PURPLE+"Server awaiting connection..."+RESET);


                  while(true){
	               Socket client =server.accept();
                       ServerLogger.log(GREEN+"Deploying Beacon......"+RESET);
          
                  
                       Thread clients= new Thread(new ClientHandler(client));
                       clients.setName(GREEN+"Client-"+client.getInetAddress()+RESET);
                       clients.start();
                   }
              }catch(IOException e){
                    ServerLogger.log(RED+"Server error:"+e.getMessage()+RESET);
              }
	}
}