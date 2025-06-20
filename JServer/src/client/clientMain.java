package client;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import org.json.JSONObject;


public class clientMain {
      private static final String RESET ="\u001B[0m";
      private static final String GREEN ="\u001B[90m";
      private static final String RED ="\u001B[91m";
      private static final String BLACK ="\u001B[90m";


      public static void main(String[] args) {
             String host ="localhost";
             int port = 9999;

             try(
                 Socket socket = new Socket(host,port);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out=new PrintWriter(socket.getOutputStream(), true);
                 Scanner scanner =new Scanner(System.in);
                 ){
                       System.out.println(GREEN+"Connected to server!"+RESET);

                       System.out.println(GREEN+"Enter Username: "+ RESET);
                       String username = scanner.nextLine();
                      
                         
                        JSONObject login =new JSONObject();
                        login.put("type","login");
                        login.put("username",username); 
                        out.println(login.toString());
                        

                        String serverResponse = in.readLine();
                        System.out.println(GREEN+"Server: "+ serverResponse+RESET);

                        while(true) {
                               System.out.print(GREEN+"> "+RESET);
                               String msg = scanner.nextLine();

                               if (msg.equalsIgnoreCase("exit")) break;

                               JSONObject message = new JSONObject();
                               message.put("type","broadcast");
                               message.put("message", msg);
                               
                               
                               out.println("{\"type\":\"ping\"}");
                               out.println(message.toString());
                               

                               String reply =in.readLine();
                               if(reply != null) {
                                      System.out.println(GREEN+"Server: " +reply+RESET);
                               }
                         }
                  }catch(IOException e) {
                        System.out.println(RED+"Connection error: "+e.getMessage()+RESET);
                  }
              }
}