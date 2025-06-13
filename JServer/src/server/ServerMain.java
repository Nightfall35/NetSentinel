package server;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerMain {
       public static Map<String, ClientHandler> clients =new ConcurrentHashMap<>();

       public static void main(String[] args) throws InterruptedException {
	      int port =9999;
	      System.out.println("Server initialized\nAcquiring port...\nport obtained : "+port+"...");
	      
	      try(ServerSocket server = new ServerSocket(port)) {
                  System.out.println("Server awaiting connection...");
                  while(true){
	               Socket client =server.accept();
                       
		       System.out.println("Client connection established: "+client.getInetAddress());
		  
                       Thread clients= new Thread(new ClientHandler(client));
                       clients.setName("Client-"+client.getInetAddress());
                       clients.start();
                   }
              }catch(IOException e){
                   System.out.println("Server error:"+e.getMessage());
              }
	}
}