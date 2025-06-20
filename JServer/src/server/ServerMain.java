package server;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerMain {
       private static final String RESET ="\u001B[0m";
       private static final String RED ="\u001B[31m";
       private static final String GREEN ="\u001B[32m";
       private static final String PURPLE ="\u001B[35m";


       public static Map<String, ClientHandler> clients =new ConcurrentHashMap<>();

       public static void main(String[] args) throws InterruptedException {
	      int port =9999;
	      ServerLogger.log(PURPLE+"Server initialized\nAcquiring port...\nport obtained : "+port+"..."+RESET);
	      
	      try(ServerSocket server = new ServerSocket(port)) {
                  ServerLogger.log(PURPLE+"Server awaiting connection..."+RESET);


                  while(true){
	               Socket client =server.accept();
                       
		        ServerLogger.log(GREEN+"Client connection established: "+client.getInetAddress()+RESET);
		  
                       Thread clients= new Thread(new ClientHandler(client));
                       clients.setName(GREEN+"Client-"+client.getInetAddress()+RESET);
                       clients.start();
                   }
              }catch(IOException e){
                    ServerLogger.log(RED+"Server error:"+e.getMessage()+RESET);
              }
	}
}