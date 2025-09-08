package com.weather;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;

class GETClient {
    private int lamportClock = 0;
    private final String serverUrl;
    private final String stationFilter;

    public GETClient(String serverUrl, String stationFilter) {
        this.serverUrl = normalizeUrl(serverUrl);
        this.stationFilter = stationFilter;
    }

    public static void main(String[] args) {
//        if (args.length < 1) {
//            System.err.println("Usage: java GETClient <server_url> [station_id]");
//            System.exit(1);
//        }
//
//        String stationFilter = args.length > 1 ? args[1] : null;
//        Client client = new Client(args[0], stationFilter);
        GETClient client = new GETClient("localhost:4567","IDS60902");
        client.fetchWeatherData();
    }

    public void fetchWeatherData() {
        try {
            lamportClock++;

            // Parse server URL
            URL url = new URL(serverUrl);
            String host = url.getHost();
            int port = url.getPort() == -1 ? 4567 : url.getPort();

            // Send GET request
            try (Socket socket = new Socket(host, port);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Send HTTP GET request
                out.println("GET /weather.json HTTP/1.1");
                out.println("Host: " + host + ":" + port);
                out.println("User-Agent: ATOMClient/1.0");
                out.println("Lamport-Clock: " + lamportClock);
                out.println();
                out.flush();

                // Read response headers
                String responseLine = in.readLine();
                if (responseLine == null) {
                    System.err.println("No response from server");
                    return;
                }

                String[] parts = responseLine.split(" ", 3);
                if (parts.length >= 2) {
                    int statusCode = Integer.parseInt(parts[1]);

                    if (statusCode != 200) {
                        System.err.println("Error response: " + statusCode);
                        return;
                    }
                }

                // Skip headers
                String line;
                int contentLength = 0;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    if (line.toLowerCase().startsWith("content-length:")) {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    }
                }

                // Read response body
                char[] buffer = new char[contentLength];
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int read = in.read(buffer, totalRead, contentLength - totalRead);
                    if (read == -1) break;
                    totalRead += read;
                }

                String jsonData = new String(buffer, 0, totalRead);
                displayWeatherData(jsonData);

            }
        } catch (Exception e) {
            System.err.println("Error fetching weather data: " + e.getMessage());
        }
    }

    private void displayWeatherData(String jsonData) {
        try {
            System.out.println(jsonData);
        } catch (Exception e) {
            System.err.println("Error displaying weather data: " + e.getMessage());
        }
    }

    private String normalizeUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "http://" + url;
        }
        return url;
    }
}
