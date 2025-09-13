package com.weather;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import utils.Utils;
import utils.WeatherData;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.List;
import java.util.Scanner;


public class GETClient {
    private static final int DEFAULT_PORT = 8080;
    private int lamportClock = 0;
    private final String serverUrl;

    public GETClient(String serverUrl) {
        this.serverUrl = normalizeUrl(serverUrl);
    }

    public static void main(String[] args) {
//        new GETClient("http://localhost:8080").fetchWeatherData(null);
        if (args.length < 1) {
            System.err.println("Usage: java GETClient <server_url>");
            System.exit(1);
        }

        GETClient client = new GETClient(args[0]);

        // Interactive loop
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("\nEnter station_id (or press Enter for all, type 'exit' to quit): ");
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("exit")) {
                System.out.println("Closing client...");
                break;
            }
            String stationID = input.isEmpty() ? null : input;
            client.fetchWeatherData(stationID);
        }
    }

    public String fetchWeatherData(String stationID) {
        try {
            lamportClock++;

            // Parse server URL
            URL url = new URL(serverUrl);
            String host = url.getHost();
            int port = url.getPort() == -1 ? DEFAULT_PORT : url.getPort();

            // Send GET request
            try (Socket socket = new Socket(host, port);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Send HTTP GET request
//                out.println("GET /weather.json HTTP/1.1");
                if (stationID == null) {
                    out.println("GET /weather.json HTTP/1.1");
                } else {
                    out.println("GET /weather.json?stationID=" + stationID + " HTTP/1.1");
                }

                out.println("Host: " + host + ":" + port);
                out.println("User-Agent: ATOMClient/1.0");
                out.println("Lamport-Clock: " + lamportClock);
                out.println("Connection: keep-alive");
                out.println();
                out.flush();

                // Read response status line
                String responseLine = in.readLine();
                if (responseLine == null) {
                    System.err.println("No response from server");
                    return null;
                }

                String[] parts = responseLine.split(" ", 3);
                if (parts.length >= 2) {
                    int statusCode = Integer.parseInt(parts[1]);
                    if (statusCode != 200) {
                        System.err.println("Error response: " + statusCode);
                        return null;
                    }
                }

                // Read headers
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
                System.out.println("===== Fetched weather Data ========");
                System.out.println(Utils.toPretty(jsonData));
                return jsonData;
            }
        } catch (Exception e) {
            System.err.println("Error fetching weather data: " + e.getMessage());
        }
        return null;
    }

    private String normalizeUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "http://" + url;
        }
        return url;
    }
}
