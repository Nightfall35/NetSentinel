package server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class BeaconBroadcaster {

    private static final int CLIENT_BEACON_PORT =8888;
    private static final int PULSE_INTERVAL_MS=2000;

    public static void main(String[] args) {
        
        String beaconIp;

        try(BufferedReader reader =new BufferedReader(new FileReader("public_ip.txt"))) {
            beaconIp =reader.readLine().trim();

        }catch(IOException e) {
           ServerLogger.log("Failed to read public ip from file");
           return;
        }

         ServerLogger.log("[*] Starting UDP beacon pulses to all clients every " +(PULSE_INTERVAL_MS/1000)+"S...");

        try(DatagramSocket socket =new DatagramSocket()) {
            socket.setBroadcast(true);
            InetAddress clientAddress = InetAddress.getByName("255.255.255.255");

            String beaconJson= String.format( 
                "{\"type\":\"beacon\", \"public_ip\":\":\"%s\",\"tcp_port\":9999}",beaconIp
                );

            byte[] buffer = beaconJson.getBytes();
               while(true) {
                   DatagramPacket packet = new DatagramPacket(buffer, buffer.length,clientAddress, CLIENT_BEACON_PORT);
                   socket.send(packet);
                   ServerLogger.log("[*] Beacon sent ");
           
                   try {
                       Thread.sleep(PULSE_INTERVAL_MS);
                   }catch(InterruptedException ignored) {}
                }

         }catch (IOException e) {
               ServerLogger.log("[!] Failed to send beacon: " +e.getMessage());
         }
    }
}
        

        