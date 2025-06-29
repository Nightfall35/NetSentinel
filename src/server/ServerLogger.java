package server;


import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ServerLogger 
{
      private static final String LOG_FILE ="Server_logs.txt";
      private static final DateTimeFormatter FORMATTER =DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
      

      public static synchronized void log(String message) {
          try(FileWriter fw =new FileWriter(LOG_FILE, true);
              BufferedWriter bw = new BufferedWriter(fw)) {
               

              String timestamp =LocalDateTime.now().format(FORMATTER);
              bw.write("[" +timestamp +"] " + message + "\n");
          }catch(IOException e) {
              System.out.println("Logger error: " +e.getMessage());
          }
      }
}