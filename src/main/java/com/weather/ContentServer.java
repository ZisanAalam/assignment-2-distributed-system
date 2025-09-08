package com.weather;

import utils.FileUtils;
import utils.WeatherData;
import java.io.*;
import java.net.Socket;
import java.net.URL;

public class ContentServer {
    private final String serverUrl;
    private final String filePath;
    private int lamportClock;
    private WeatherData  weatherData;

    public ContentServer(String serverUrl, String filePath) {
        this.serverUrl = parseServerUrl(serverUrl);
        this.filePath = filePath;
        this.lamportClock = 0;
    }

    public static void main(String[] args) {
//        if (args.length < 2) {
//            System.err.println("Usage: java ContentServer <server_url> <data_file>");
//            System.exit(1);
//        }
//
//        ContentServer server = new ContentServer(args[0], args[1]);
////        server.start();

        String filePath = "src/main/resources/cs_weather_data.txt";
        String serverUrl = "localhost:4567";


        new ContentServer(serverUrl,filePath).start();
    }

    public void start() {
        try {
            loadWeatherData();
            sendWeatherUpdate();
        } catch (Exception e) {
            System.err.println("Content server error: " + e.getMessage());
        }
    }

    private String parseServerUrl(String url) {
        // Handle various URL formats
        if (!url.startsWith("http://")) {
            if (url.contains(":")) {
                url = "http://" + url;
            } else {
                url = "http://" + url + ":4567";
            }
        }
        return url;
    }

    private void loadWeatherData() throws IOException {
        String fileContent = FileUtils.readCSWeatherFile(filePath);
        if(fileContent == null){
            System.err.println("Failed to read weather data from file: " + filePath);
            return;
        }
        weatherData = FileUtils.parseWeatherData(fileContent);
    }

    private void sendWeatherUpdate() {
        try {
            lamportClock++;
            weatherData.updateTimestamp();
            String jsonData = weatherData.toJson();

            // Parse server URL
            URL url = new URL(serverUrl);
            String host = url.getHost();
            int port = url.getPort() == -1 ? 4567 : url.getPort();

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
                        System.out.println("Update sent for " + weatherData.getId() +
                                " (clock=" + lamportClock + ") - Status: " + statusCode);

                        if (statusCode == 400) {
                            System.err.println("Out-of-order request rejected. Resetting clock.");
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error sending update: " + e.getMessage());
        }
    }
}
