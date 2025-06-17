package server;

import java.io.*;
import java.net.*;
import org.json.JSONObject;


public class ClientHandler implements Runnable {
        private Socket client;
        private PrintWriter out;
        private String username;
	
	public ClientHandler(Socket client) {
              this.client=client;
        }

        @Override
        public void run() {
               try(
                  BufferedReader in =new BufferedReader(
                     new InputStreamReader(client.getInputStream()));
                  PrintWriter out =new PrintWriter(
                     client.getOutputStream(),true)
                ) {
                      this.out =out;
                      String input;
                      while ((input = in.readLine()) != null) { 
			JSONObject message =new JSONObject(input);
                        String type =message.getString("type");
                        
                        switch(type) {
                             case "login":
                                 handleLogin(message);
                                 break;
                             case "message":
                                 handlePrivateMessage(message);
                                 break;
                             case "broadcast":
                                 handleBroadcast(message);
                                 break;
                             default:
                                 sendError("Unknown message type"+ type);
                         }

                       }                      
                 }catch (IOException e) {
                       ServerLogger.log("Connection with client: "+client.getInetAddress() +"closed");
                 }finally{
                      try{
                              if(username != null)
			          ServerMain.clients.remove(username);
          
                              client.close();
                      }catch(IOException e) {
                            //ignore
                      }
                 }
           }
           
           private void handleLogin(JSONObject message) {
                  this.username =message.getString("from");
                 
                  if(ServerMain.clients.containsKey(username)) {
                       sendError("username already taken .");
                  }else{
                       ServerMain.clients.put(username,this);
                       sendInfo("Login successful as" +username);
                  }
           }

           private void handlePrivateMessage(JSONObject message) {
                   String to =message.getString("to");
                   String body =message.getString("body");


                   ClientHandler recipient =ServerMain.clients.get(to);
                   if(recipient != null) {
                         JSONObject reply =new JSONObject();
                         reply.put("type", "message");
                         reply.put("from", username);
                         reply.put("body", body);
                         recipient.send(reply.toString());
                    }else{
                         sendError("user '" +to+"' not found.");
                    }
           }
 
 
           private void  handleBroadcast(JSONObject message) {
                   String body =message.getString("body");
       
                   JSONObject broadcastMsg = new JSONObject();
                   broadcastMsg.put("type", "broadcast");
                   broadcastMsg.put("from", username);
                   broadcastMsg.put("body", body);
         
                   for(ClientHandler handler : ServerMain.clients.values()) {
                       if(!handler.username.equals(this.username)) {
                               handler.send(broadcastMsg.toString());
                       }
                   }
            }

            //Utility Methods below 
            
            private void send(String json) {
                 out.println(json);
            }

            private void sendError(String errorMsg){
                    JSONObject error = new JSONObject();
                    error.put("type", "error");
                    error.put("body",errorMsg);
                    send(error.toString());
            }

            private void sendInfo(String infoMsg) {
                  JSONObject info = new JSONObject();
                  info.put("type","info");
                  info.put("body", infoMsg);
                  send(info.toString());
            }
}