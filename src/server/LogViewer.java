package server;

import java.io.*;

public class LogViewer {
    public static void main(String[] args) {
        File logFile =new File("server_logs.txt");
      
        try(BufferedReader reader =new BufferedReader(new FileReader((logFile)))) {
                String line;
                long lastLength = 0;
 
                while(true){
                   while((line = reader.readLine()) !=null){
                          System.out.println(line);
                   }

                   Thread.sleep(1000);


                   if(logFile.length() < lastLength) {
                       reader.close();
                       Thread.sleep(1000);
                  }

                  lastLength =logFile.length();

                }
         }catch(Exception e) {
              e.printStackTrace();
         }
     }
}