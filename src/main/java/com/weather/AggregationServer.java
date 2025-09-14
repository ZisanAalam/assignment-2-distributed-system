package com.weather;

import com.google.gson.Gson;
import utils.FileUtils;
import utils.WeatherData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;


public class AggregationServer {
    private static final int DEFAULT_PORT = 4567;
    private static final int EXPIRY_SECONDS = 30;

    private final int port;
    private final Map<String, Integer> contentServerClocks = new HashMap<>();
    private final Map<String, Integer> clientClocks = new HashMap<>();
    private final BlockingQueue<Request> requestQueue = new LinkedBlockingQueue<>();
    private final ExecutorService threadPool = Executors.newFixedThreadPool(10);
    private final Object fileLock = new Object();
    private volatile boolean running = true;
    private final Gson gson = new Gson();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ServerSocket serverSocket;
    private final CountDownLatch started = new CountDownLatch(1);

    public AggregationServer() {
        this.port = DEFAULT_PORT;
    }

    public AggregationServer(int port) {
        this.port = port;
    }

    public static void main(String[] args) {

        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default: " + DEFAULT_PORT);
            }
        }

        AggregationServer server = new AggregationServer(port);
        server.start();
    }

    public void start() {
        System.out.println("Starting Aggregation Server on port " + port);

        threadPool.submit(this::processRequests);
        threadPool.submit(this::manageExpiredData);

        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server listening on port " + port);
            started.countDown(); // 🔑 signal server is ready

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.submit(() -> handleConnection(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error accepting connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Server socket error: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }
    public void waitUntilStarted() throws InterruptedException {
        started.await(); // block until countDown() called
    }

    private void handleConnection(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String requestLine = in.readLine();
            if (requestLine == null) return;

            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                String[] parts = line.split(": ", 2);
                if (parts.length == 2) {
                    headers.put(parts[0].toLowerCase(), parts[1]);
                }
            }

            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 3) {
                sendResponse(out, 400, "Bad Request", "");
                return;
            }

            String method = requestParts[0];
            if ("GET".equals(method)) {
                String path = requestParts[1];
                System.out.println("Path :: "+path);
                handleGetRequest(out, headers, socket.getRemoteSocketAddress().toString(), path);
            } else if ("PUT".equals(method)) {
                handlePutRequest(in, out, headers);
            } else {
                sendResponse(out, 400, "Bad Request", "");
            }

        } catch (IOException e) {
            System.err.println("Connection handling error: " + e.getMessage());
        }
    }

    private void handleGetRequest(PrintWriter out, Map<String, String> headers, String clientId, String path) {
        try {
            int clockValue = Integer.parseInt(headers.getOrDefault("lamport-clock", "0"));

            // Parse query param
            String stationId = null;
            if (path.contains("?")) {
                String[] parts = path.split("\\?", 2);
                if (parts.length == 2) {
                    String[] queryParams = parts[1].split("&");
                    for (String param : queryParams) {
                        String[] kv = param.split("=", 2);
                        if (kv.length == 2 && kv[0].equals("stationID")) {
                            stationId = kv[1];
                        }
                    }
                }
            }

            Request request = new Request(Request.Type.GET, clientId, clockValue, stationId);

            if (!requestQueue.offer(request)) {
                sendResponse(out, 503, "Service Unavailable", "Server is overloaded, try again later.");
                return;
            }

            String result = request.waitForResult();
            if (result.startsWith("ERROR:")) {
                int statusCode = Integer.parseInt(result.substring(6));
                sendResponse(out, statusCode, getStatusMessage(statusCode), "");
            } else {
                sendResponse(out, 200, "OK", result);
            }
        } catch (Exception e) {
            sendResponse(out, 500, "Internal Server Error", "");
        }
    }

    private void handlePutRequest(BufferedReader in, PrintWriter out, Map<String, String> headers) {
        try {
            int clockValue = Integer.parseInt(headers.getOrDefault("lamport-clock", "0"));
            int contentLength = Integer.parseInt(headers.getOrDefault("content-length", "0"));

            if (contentLength == 0) {
                sendResponse(out, 204, "No Content", "");
                return;
            }

            char[] buffer = new char[contentLength];
            int totalRead = 0;
            while (totalRead < contentLength) {
                int read = in.read(buffer, totalRead, contentLength - totalRead);
                if (read == -1) break;
                totalRead += read;
            }

            String jsonData = new String(buffer, 0, totalRead);
            Request request = new Request(Request.Type.PUT, null, clockValue, jsonData);

            if (!requestQueue.offer(request)) {
                sendResponse(out, 503, "Service Unavailable", "Server is overloaded, try again later.");
                return;
            }

            String result = request.waitForResult();
            if (result.startsWith("ERROR:")) {
                int statusCode = Integer.parseInt(result.substring(6));
                sendResponse(out, statusCode, getStatusMessage(statusCode), "");
            } else {
                int statusCode = Integer.parseInt(result);
                sendResponse(out, statusCode, getStatusMessage(statusCode), "");
            }
        } catch (Exception e) {
            sendResponse(out, 500, "Internal Server Error", "");
        }
    }

    private void processRequests() {
        while (running) {
            try {
                Request request = requestQueue.take();
                if (request.type == Request.Type.PUT) processPutRequest(request);
                else if (request.type == Request.Type.GET) processGetRequest(request);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error processing request: " + e.getMessage());
            }
        }
    }

    private void processPutRequest(Request request) {
        try {
            WeatherData weatherData = gson.fromJson(request.data, WeatherData.class);
            String stationId = weatherData.getId();

            if (stationId == null) {
                request.setResult("ERROR:400");
                return;
            }

            int lastClock = contentServerClocks.getOrDefault(stationId, 0);
            if (request.clockValue <= lastClock) {
                request.setResult("ERROR:400");
                return;
            }

            contentServerClocks.put(stationId, request.clockValue);
            weatherData.setLastUpdated(System.currentTimeMillis() / 1000);

            synchronized (fileLock) {
                List<WeatherData> allData = FileUtils.loadWeatherData();
                allData.removeIf(d -> stationId.equals(d.getId()));
                allData.add(weatherData);
                FileUtils.saveWeatherData(allData);
            }

            boolean isFirstTime = lastClock == 0;
            request.setResult(isFirstTime ? "201" : "200");
        } catch (Exception e) {
            request.setResult("ERROR:500");
        }
    }

    private void processGetRequest(Request request) {
        try {
            int lastClock = clientClocks.getOrDefault(request.entityId, 0);
            if (request.clockValue <= lastClock) {
                request.setResult("ERROR:400");
                return;
            }

            clientClocks.put(request.entityId, request.clockValue);

            synchronized (fileLock) {
                List<WeatherData> allData = FileUtils.loadWeatherData();

                allData.removeIf(d -> d.isExpired(EXPIRY_SECONDS));

                String stationId = request.data; // optional
                List<WeatherData> filteredData;
                if (stationId == null || stationId.isEmpty()) {
                    filteredData = allData; // return all
                } else {
                    filteredData = allData.stream()
                            .filter(d -> stationId.equals(d.getId()))
                            .collect(Collectors.toList());
                }

                String jsonResult = gson.toJson(filteredData);
                request.setResult(jsonResult);
            }
        } catch (Exception e) {
            request.setResult("ERROR:500");
        }
    }

    private void manageExpiredData() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                synchronized (fileLock) {
                    List<WeatherData> allData = FileUtils.loadWeatherData();
                    List<String> expiredStations = new ArrayList<>();

                    allData.removeIf(d -> {
                        boolean expired = d.isExpired(EXPIRY_SECONDS);
                        if (expired) expiredStations.add(d.getId());
                        return expired;
                    });

                    if (!expiredStations.isEmpty()) {
                        FileUtils.saveWeatherData(allData);
                        expiredStations.forEach(contentServerClocks::remove);
                        System.out.println("Expired stations removed: " + expiredStations);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error managing expired data: " + e.getMessage());
            }
        }, 0, 10, TimeUnit.SECONDS); // initial delay = 0, repeat every 10s
    }

    private void sendResponse(PrintWriter out, int statusCode, String statusMessage, String body) {
        out.println("HTTP/1.1 " + statusCode + " " + statusMessage);
        out.println("Content-Type: application/json");
        out.println("Content-Length: " + body.length());
        out.println();
        out.print(body);
        out.flush();
    }

    private String getStatusMessage(int statusCode) {
        switch (statusCode) {
            case 200: return "OK";
            case 201: return "Created";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 500: return "Internal Server Error";
            default: return "Unknown";
        }
    }

    private static class Request {
        enum Type { GET, PUT }
        final Type type;
        final String entityId;
        final int clockValue;
        final String data;
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile String result;

        Request(Type type, String entityId, int clockValue, String data) {
            this.type = type;
            this.entityId = entityId;
            this.clockValue = clockValue;
            this.data = data;
        }

        void setResult(String result) {
            this.result = result;
            latch.countDown();
        }

        String waitForResult() throws InterruptedException {
            latch.await();
            return result;
        }
    }

    public void stop() {
        running = false; // stop accepting new requests

        // Close server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                System.out.println("Server socket closed.");
            } catch (IOException e) {
                System.err.println("Error closing server socket: " + e.getMessage());
            }
        }

        // Shutdown thread pool
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
            System.out.println("Thread pool shutdown complete.");
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Shutdown scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            System.out.println("Scheduler shutdown complete.");
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Clear queues and clocks
        requestQueue.clear();
        contentServerClocks.clear();
        clientClocks.clear();
        FileUtils.deleteDataFile();
        System.out.println("Server cleanup complete.");
    }

}

