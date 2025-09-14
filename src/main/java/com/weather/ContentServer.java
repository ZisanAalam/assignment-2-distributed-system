package com.weather;

import utils.FileUtils;
import utils.WeatherData;
import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.Scanner;


/**
 * ContentServer acts as a weather data publisher in the distributed system.
 * It reads weather data from files and sends it to the AggregationServer
 * using HTTP PUT requests with Lamport clock synchronization.
 * Key Features:
 * - Lamport clock implementation for causal ordering
 * - Interactive and programmatic modes of operation
 * - File-based weather data input (CSV format)
 * - HTTP client implementation for data transmission
 * - Graceful error handling and resource cleanup
 */
public class ContentServer {
    //default port
    private static final int DEFAULT_PORT = 4567;
    //aggregation server url
    private final String serverUrl;
    //lamport clock for synchronization
    private int lamportClock;
    //Weather data object
    private WeatherData weatherData;
    // Scanner for interactive user input
    private final Scanner scanner;
    // Flag to determine if running in interactive mode
    private boolean interactiveMode;

    /**
     * Constructor with server URL and initial data file
     *
     * @param serverUrl URL of the AggregationServer to send data to
     * @param filePath Initial weather data file to load
     */
    public ContentServer(String serverUrl, String filePath) {
        this.serverUrl = parseServerUrl(serverUrl);
        //initialize lamport clock to zero
        this.lamportClock = 0;
        this.interactiveMode = false;
        scanner =  new Scanner(System.in);

        //Initialize weather data from file
        try {
            loadWeatherData(filePath);
        } catch (IOException e) {
            System.err.println("Failed to load initial weather data: " + e.getMessage());
        }
    }

    /**
     * Main method
     * Validates command line arguments and starts the server
     *
     * @param args Command line arguments: [server_url] [data_file]
     */
    public static void main(String[] args) {
        ContentServer server;

        // Validate command line arguments
        if(args.length != 2) {
            System.err.println("Usage: java ContentServer [<server_url> <data_file>]");
            System.exit(1);
        }

        // create ContentServer instance with provided arguments
        server = new ContentServer(args[0], args[1]);
        server.start();

        // start interactive mode for continuous operation
        server.startInteractive();
    }

    /**
     * Starts interactive mode allowing continuous data updates
     * Prompts user for file paths and sends weather data to server
     */
    public void startInteractive() {
        this.interactiveMode = true;
        System.out.println("=== Interactive Content Server ===");
        System.out.println("Press Ctrl+C to terminate");
        System.out.println();

        // Get initial file path
        System.out.print("Enter weather data file path: ");
        String filePath = scanner.nextLine().trim();
        if (filePath.isEmpty()) {
            System.err.println("File path is required!");
            return;
        }

        // Load and send initial data
        try {
            loadWeatherData(filePath).sendWeatherUpdate();
        } catch (Exception e) {
            System.err.println("Error with initial data: " + e.getMessage());
            return;
        }

        System.out.println("\nNow you can continue sending updates with new file paths:");
        System.out.println("(Press Ctrl+C to exit)");

        // Interactive loop for subsequent file paths
        while (true) {
            try {
                System.out.print("\nEnter weather data file path (or press Ctrl+C to exit): ");

                // Check if input is available (handles Ctrl+C)
                if (!scanner.hasNextLine()) {
                    // EOF reached
                    break;
                }

                String nextFilePath = scanner.nextLine().trim();

                if (nextFilePath.isEmpty()) {
                    System.out.println("Empty path provided, skipping...");
                    continue;
                }

                // Load new data and send update
                loadWeatherData(nextFilePath).sendWeatherUpdate();

            } catch (Exception e) {
                System.err.println("Error processing file: " + e.getMessage());
            }
        }

        cleanup();
    }

    /**
     * Starts the ContentServer in non-interactive mode
     * Sends the currently loaded weather data once
     *
     * @return HTTP status code from the server response, -1 if error
     */
    public int start() {
        try {
            return sendWeatherUpdate();
        } catch (Exception e) {
            System.err.println("Content server error: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Starts the ContentServer with a specific file path
     * Loads data from the specified file and sends it to server
     *
     * @param filePath Path to weather data file
     * @return HTTP status code from the server response, -1 if error
     */
    public int start(String filePath) {
        try {
            return loadWeatherData(filePath).sendWeatherUpdate();
        } catch (Exception e) {
            System.err.println("Content server error: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Parses and normalizes server URL to ensure proper format
     * Handles various URL formats and adds default port if needed
     *
     * @param url Raw server URL string
     * @return Properly formatted HTTP URL
     */
    private String parseServerUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }

        // Handle various URL formats
        if (!url.startsWith("http://")) {
            if (url.contains(":")) {
                url = "http://" + url;
            } else {
                url = "http://" + url + ":"+DEFAULT_PORT;
            }
        }
        return url;
    }

    /**
     * Loads weather data from the specified file path
     * Parses text format weather data and creates WeatherData object
     *
     * @param filePath Path to the weather data file
     * @return This ContentServer instance for method chaining
     * @throws IOException If file cannot be read or parsed
     */
    private ContentServer loadWeatherData(String filePath) throws IOException {
        // Read file content using utility method
        String fileContent = FileUtils.readCSWeatherFile(filePath);
        if (fileContent == null){
            throw new IOException("Failed to read weather data from file: " + filePath);
        }
        // Parse content into WeatherData object
        weatherData = FileUtils.parseWeatherData(fileContent);

        if (interactiveMode) {
            System.out.println(" ==> Loaded weather data from: " + filePath);
        }
        return this;
    }

    /**
     * Sends weather data update to AggregationServer via HTTP PUT request
     * Implements Lamport clock synchronization and handles server responses
     *
     * @return HTTP status code from server response, -1 if error occurred
     */
    private int sendWeatherUpdate() {
        try {
            // Increment Lamport clock before sending
            lamportClock++;
            // Update timestamp in weather data
            weatherData.updateTimestamp();

            // Convert weather data to JSON format
            String jsonData = weatherData.toJson();

            // Parse server URL
            URL url = new URL(serverUrl);
            String host = url.getHost();
            int port = url.getPort() == -1 ? DEFAULT_PORT : url.getPort();

            // Establish connection and send PUT request
            try (Socket socket = new Socket(host, port);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Send HTTP PUT request headers
                out.println("PUT /weather.json HTTP/1.1");
                out.println("Host: " + host + ":" + port);
                out.println("User-Agent: TOMClient/1.0");
                out.println("Content-Type: application/json");
                out.println("Content-Length: " + jsonData.length());
                out.println("Lamport-Clock: " + lamportClock);
                out.println(); // End headers

                //send json payload
                out.print(jsonData);
                out.flush();

                // Read and process server response
                String responseLine = in.readLine();
                if (responseLine != null) {
                    // Parse HTTP response status line
                    String[] parts = responseLine.split(" ", 3);
                    if (parts.length >= 2) {
                        int statusCode = Integer.parseInt(parts[1]);
                        String statusMessage = getStatusMessage(statusCode);

                        // Display appropriate message based on mode
                        if (interactiveMode) {
                            System.out.println(" ==> Update sent for " + weatherData.getId() +
                                    " (clock=" + lamportClock + ") - " + statusMessage);
                        } else {
                            System.out.println(" ==> Update sent for " + weatherData.getId() +
                                    " (clock=" + lamportClock + ") - Status: " + statusCode);
                        }

                        // Handle clock synchronization errors
                        if (statusCode == 400) {
                            System.err.println(" ==> Clock Out-of-order. Request rejected.");
                        }

                        // Return status code
                        return statusCode;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error sending update: " + e.getMessage());
        }
        return -1; // Return -1 if failed
    }

    /**
     * Maps HTTP status codes to human-readable messages
     *
     * @param statusCode HTTP status code from server
     * @return Formatted status message string
     */
    private String getStatusMessage(int statusCode) {
        switch (statusCode) {
            case 200: return "200 OK";
            case 201: return "201 Created";
            case 400: return "400 Bad Request";
            case 500: return "500 Internal Server Error";
            default: return "Status: " + statusCode;
        }
    }

    /**
     * Performs cleanup operations when shutting down ContentServer
     * Closes scanner and displays termination message
     */
    public void cleanup() {
        if (scanner != null) {
            scanner.close();
        }
        if (interactiveMode) {
            System.out.println("\nContent Server terminated.");
        }
    }

    // Static initializer block to add shutdown hook for  termination
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down Content Server...");
        }));
    }

    /**
     * Gets the current Lamport clock value
     * Used for testing clock synchronization scenarios
     * @return Current Lamport clock value
     */
    public int getLamportClock() {
        return lamportClock;
    }

    /**
     * Decrements the Lamport clock by 1
     * Used for testing clock synchronization scenarios
     */
    public void reduceLamportClock() {
        lamportClock--;
    }
}