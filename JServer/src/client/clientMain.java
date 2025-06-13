package client;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import org.json.JSONObject;


public class clientMain {
      public static void main(String[] args) {
             String host ="localhost";
             int port = 9999;

             try(
                 Socket socket = new Socket(host,port);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out=new PrintWriter(socket.getOutputStream(), true);
                 Scanner scanner =new Scanner(System.in);
                 ){
                       System.out.println("Connected to server!");
                      
                         
                        JSONObject login =new JSONObject();
                        login.put("type","login");
                        login.put("username","cypher"); // need to update for userinput here !!!!
                        login.put("from" , "cypher");
                        out.println(login.toString());


                        String serverResponse = in.readLine();
                        System.out.println("Server: "+ serverResponse);

                        while(true) {
                               System.out.print("> ");
                               String msg = scanner.nextLine();

                               if (msg.equalsIgnoreCase("exit")) break;

                               JSONObject message = new JSONObject();
                               message.put("type","broadcast");
                               message.put("message", msg);

                               out.println(message.toString());

                               String reply =in.readLine();
                               if(reply != null) {
                                      System.out.println("Server: " +reply);
                               }
                         }
                  }catch(IOException e) {
                        System.out.println("Connection error: "+e.getMessage());
                  }
              }
}