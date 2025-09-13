package com.weather;

import utils.FileUtils;
import utils.WeatherData;
import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.Scanner;

public class ContentServer {
    private static final int DEFAULT_PORT = 8080;
    private final String serverUrl;
    private int lamportClock;
    private WeatherData weatherData;
    private final Scanner scanner;
    private boolean interactiveMode;

    public ContentServer(String serverUrl, String filePath) {
        this.serverUrl = parseServerUrl(serverUrl);
        this.lamportClock = 0;
        this.interactiveMode = false;
        scanner =  new Scanner(System.in);

        try {
            loadWeatherData(filePath);
        } catch (IOException e) {
            System.err.println("Failed to load initial weather data: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        ContentServer server;


        if(args.length != 2) {
            System.err.println("Usage: java ContentServer [<server_url> <data_file>]");
            System.exit(1);
        }

        server = new ContentServer(args[0], args[1]);
        server.start();

        server.startInteractive();
    }

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
//            sendWeatherUpdate();
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

                if (!scanner.hasNextLine()) {
                    break; // EOF reached
                }

                String nextFilePath = scanner.nextLine().trim();

                if (nextFilePath.isEmpty()) {
                    System.out.println("Empty path provided, skipping...");
                    continue;
                }

                loadWeatherData(nextFilePath).sendWeatherUpdate();

            } catch (Exception e) {
                System.err.println("Error processing file: " + e.getMessage());
            }
        }

        cleanup();
    }

    public int start() {
        try {
            return sendWeatherUpdate();
        } catch (Exception e) {
            System.err.println("Content server error: " + e.getMessage());
        }
        return -1;
    }
    public int start(String filePath) {
        try {
            return loadWeatherData(filePath).sendWeatherUpdate();
        } catch (Exception e) {
            System.err.println("Content server error: " + e.getMessage());
        }
        return -1;
    }

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

    private ContentServer loadWeatherData(String filePath) throws IOException {
        String fileContent = FileUtils.readCSWeatherFile(filePath);
        if (fileContent == null){
            throw new IOException("Failed to read weather data from file: " + filePath);
        }
        weatherData = FileUtils.parseWeatherData(fileContent);

        if (interactiveMode) {
            System.out.println(" ==> Loaded weather data from: " + filePath);
        }
        return this;
    }

    private int sendWeatherUpdate() {
        try {
            lamportClock++;
            weatherData.updateTimestamp();
            String jsonData = weatherData.toJson();

            // Parse server URL
            URL url = new URL(serverUrl);
            String host = url.getHost();
            int port = url.getPort() == -1 ? DEFAULT_PORT : url.getPort();

            // Send PUT request
            try (Socket socket = new Socket(host, port);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Send HTTP PUT request
                out.println("PUT /weather.json HTTP/1.1");
                out.println("Host: " + host + ":" + port);
                out.println("User-Agent: TOMClient/1.0");
                out.println("Content-Type: application/json");
                out.println("Content-Length: " + jsonData.length());
                out.println("Lamport-Clock: " + lamportClock);
                out.println(); // End headers
                out.print(jsonData);
                out.flush();

                // Read response
                String responseLine = in.readLine();
                if (responseLine != null) {
                    String[] parts = responseLine.split(" ", 3);
                    if (parts.length >= 2) {
                        int statusCode = Integer.parseInt(parts[1]);
                        String statusMessage = getStatusMessage(statusCode);

                        if (interactiveMode) {
                            System.out.println(" ==> Update sent for " + weatherData.getId() +
                                    " (clock=" + lamportClock + ") - " + statusMessage);
                        } else {
                            System.out.println(" ==> Update sent for " + weatherData.getId() +
                                    " (clock=" + lamportClock + ") - Status: " + statusCode);
                        }

                        if (statusCode == 400) {
                            System.err.println(" ==> Clock Out-of-order. Request rejected.");
                        }

                        return statusCode; // Return status code
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error sending update: " + e.getMessage());
        }
        return -1; // Return -1 if failed
    }

    private String getStatusMessage(int statusCode) {
        switch (statusCode) {
            case 200: return "200 OK";
            case 201: return "201 Created";
            case 400: return "400 Bad Request";
            case 500: return "500 Internal Server Error";
            default: return "Status: " + statusCode;
        }
    }

    public void cleanup() {
        if (scanner != null) {
            scanner.close();
        }
        if (interactiveMode) {
            System.out.println("\nContent Server terminated.");
        }
    }

    // Add shutdown hook for graceful termination
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down Content Server...");
        }));
    }

    public int getLamportClock() {
        return lamportClock;
    }

    public void reduceLamportClock() {
        lamportClock--;
    }
}