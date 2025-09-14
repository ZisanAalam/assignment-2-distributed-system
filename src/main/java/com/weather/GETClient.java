package com.weather;

import utils.Utils;
import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.Scanner;


/**
 * GETClient acts as a weather data consumer in the distributed system.
 * It requests weather data from the AggregationServer using HTTP GET requests
 * with Lamport clock synchronization for causal consistency.
 * Key Features:
 * - Lamport clock implementation for distributed synchronization
 * - Interactive mode for continuous data retrieval
 * - Optional station-specific filtering
 * - HTTP client implementation with proper header handling
 * - JSON deserialization and pretty-printing
 */
public class GETClient {
    //default port
    private static final int DEFAULT_PORT = 4567;
    // Lamport logical clock for maintaining ordering
    private int lamportClock = 0;
    // URL of the AggregationServer
    private final String serverUrl;

    /**
     * Constructor initializes GETClient with target server URL
     *
     * @param serverUrl URL of the AggregationServer to connect to
     */
    public GETClient(String serverUrl) {
        this.serverUrl = normalizeUrl(serverUrl);
    }

    /**
     * Main method
     * Provides interactive interface for requesting weather data
     *
     * @param args Command line arguments: [server_url]
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java GETClient <server_url>");
            System.exit(1);
        }

        // Create GETClient instance with provided server URL
        GETClient client = new GETClient(args[0]);

        // Interactive loop for continuous operation
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("\nEnter station_id (or press Enter for all, type 'exit' to quit): ");
            String input = scanner.nextLine().trim();

            // Handle exit command
            if (input.equalsIgnoreCase("exit")) {
                System.out.println("Closing client...");
                break;
            }
            String stationID = input.isEmpty() ? null : input;
            client.fetchWeatherData(stationID);
        }
    }

    /**
     * Fetches weather data from the AggregationServer
     * Implements HTTP GET request with Lamport clock synchronization
     *
     * @param stationID Optional station ID filter (null for all stations)
     * @return JSON string response from server, null if error occurred
     */
    public String fetchWeatherData(String stationID) {
        try {
            // Increment Lamport clock before making request
            lamportClock++;

            // Parse server URL
            URL url = new URL(serverUrl);
            String host = url.getHost();
            int port = url.getPort() == -1 ? DEFAULT_PORT : url.getPort();

            // Establish connection and send GET request
            try (Socket socket = new Socket(host, port);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Send HTTP GET request
                if (stationID == null) {
                    // Request all weather data
                    out.println("GET /weather.json HTTP/1.1");
                } else {
                    // Request data for specific station
                    out.println("GET /weather.json?stationID=" + stationID + " HTTP/1.1");
                }

                // Send HTTP headers
                out.println("Host: " + host + ":" + port);
                out.println("User-Agent: ATOMClient/1.0");
                out.println("Lamport-Clock: " + lamportClock);
                out.println("Connection: keep-alive");
                out.println();
                out.flush();

                // Read and validate response status line
                String responseLine = in.readLine();
                if (responseLine == null) {
                    System.err.println("No response from server");
                    return null;
                }

                // Parse HTTP response status
                String[] parts = responseLine.split(" ", 3);
                if (parts.length >= 2) {
                    int statusCode = Integer.parseInt(parts[1]);
                    if (statusCode != 200) {
                        System.err.println("Error response: " + statusCode);
                        return null;
                    }
                }

                // Read response headers to get content length
                String line;
                int contentLength = 0;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    if (line.toLowerCase().startsWith("content-length:")) {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    }
                }

                // Read response body based on content length
                char[] buffer = new char[contentLength];
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int read = in.read(buffer, totalRead, contentLength - totalRead);
                    if (read == -1) break;
                    totalRead += read;
                }

                // Convert response to string and display formatted output
                String jsonData = new String(buffer, 0, totalRead);
                System.out.println("===== Fetched weather Data ========");
                System.out.println(Utils.toPretty(jsonData));
                return jsonData;
            }
        } catch (Exception e) {
            System.err.println("Error fetching weather data: " + e.getMessage());
        }
        return null;
    }

    /**
     * Normalizes URL format to ensure proper HTTP protocol prefix
     *
     * @param url Raw URL string from user input
     * @return Properly formatted HTTP URL
     */
    private String normalizeUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "http://" + url;
        }
        return url;
    }
}
