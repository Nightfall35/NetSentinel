package server;

import java.io.*;
import java.net.*;

public class ServerMain {
       public static void main(String[] args) throws InterruptedException {
	      int port =9999;
	      System.out.println("Server initialized\nAcquiring port...\nport obtained : "+port+"...");
	      Thread.sleep(1000);
	      try(ServerSocket server = new ServerSocket(port)) {
                  System.out.println("Server awaiting connection...");
	          Socket client =server.accept();
		  System.out.println("Client connection established: "+client.getInetAddress());
		  
                  //receives data from client(for now use telnet or netcat) modify to multithread later
                  BufferedReader in =new BufferedReader(
			new InputStreamReader(client.getInputStream()));
		  
                  //send back data 	
                  PrintWriter out =new PrintWriter (client.getOutputStream(),true);
		  

		  String input;
                  while((input = in.readLine()) != null){
		       System.out.println("Received: " +input);
		       out.println("Echo: " +input);
                       Thread.sleep(1000);
                  }
              }catch(IOException e) {
		e.printStackTrace();
	      }
	}
}