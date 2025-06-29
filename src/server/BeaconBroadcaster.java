package server;

import java.io.*;
import java.net.*;

public class BeaconBroadcaster {

    private static final int CLIENT_BEACON_PORT = 8888;
    private static final int PULSE_INTERVAL_MS = 2000; // 5 seconds

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java BeaconBroadcaster <client-ip>");
            return;
        }

        String clientIp = args[0];
        String beaconIp;

        try (BufferedReader reader = new BufferedReader(new FileReader("public_ip.txt"))) {
            beaconIp = reader.readLine().trim();
        } catch (IOException e) {
            System.err.println("Failed to read public IP from file.");
            return;
        }

        System.out.println("[*] Starting beacon pulses to " + clientIp + " every " + (PULSE_INTERVAL_MS / 1000) + "s...");

        while (true) {
            try (Socket socket = new Socket(clientIp, CLIENT_BEACON_PORT);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                out.println(beaconIp);
                System.out.println("[✓] Beacon sent to " + clientIp);

            } catch (IOException e) {
                System.out.println("[!] Beacon failed to " + clientIp + ": " + e.getMessage());
            }

            try {
                Thread.sleep(PULSE_INTERVAL_MS);
            } catch (InterruptedException ignore) {}
        }
    }
}
