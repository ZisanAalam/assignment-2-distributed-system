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


/**
 * AggregationServer is the central server in the distributed weather system.
 * It receives weather data from ContentServers via PUT requests and save/update in weather.json file
 * Send weather data to GETClients via GET requests.
 * Key Features:
 * - Lamport clock synchronization for distributed consistency
 * - Multi-threaded request processing using thread pools
 * - Automatic expiration of expired weather data
 * - Thread-safe file operations for data persistence
 * - HTTP/1.1 protocol implementation
 */

public class AggregationServer {
    // Default server port
    private static final int DEFAULT_PORT = 4567;

    // Data expiry time in seconds - weather data older than this is removed
    private static final int EXPIRY_SECONDS = 30;

    private final int port;

    // Lamport clock tracking for each content server to ensure proper ordering
    private final Map<String, Integer> contentServerClocks = new HashMap<>();

    // Lamport clock tracking for each client to prevent duplicate processing
    private final Map<String, Integer> clientClocks = new HashMap<>();

    // Thread-safe queue for processing requests asynchronously
    private final BlockingQueue<Request> requestQueue = new LinkedBlockingQueue<>();

    // Thread pool for handling multiple concurrent connections
    private final ExecutorService threadPool = Executors.newFixedThreadPool(10);

    // Synchronization object for file operations to prevent race conditions
    private final Object fileLock = new Object();

    // Server running state
    // declared volatile so changes made by one thread are immediately visible to all other threads running the server.
    private volatile boolean running = true;

    private final Gson gson = new Gson();
    // Scheduler for periodic cleanup of expired data
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Server socket for accepting client connections
    private ServerSocket serverSocket;

    // Synchronization primitive to signal when server is ready
    private final CountDownLatch started = new CountDownLatch(1);

    /**
     * Default constructor
     */
    public AggregationServer() {
        this.port = DEFAULT_PORT;
    }

    /**
     * Constructor with custom port
     * @param port The port number for the server to listen on
     */
    public AggregationServer(int port) {
        this.port = port;
    }


    /**
     * Main method to start the aggregation server
     * @param args Command line arguments - custom port number
     */
    public static void main(String[] args) {

        int port = DEFAULT_PORT;

        // Parse command line argument for custom port
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

    /**
     * Starts the aggregation server and begins accepting connections
     * Initializes background tasks for request processing and data cleanup
     */
    public void start() {
        System.out.println("Starting Aggregation Server on port " + port);

        // Submit worker thread to continuously process incoming requests
        threadPool.submit(this::processRequests);
        // Submit worker thread to periodically clean up expired data
        threadPool.submit(this::manageExpiredData);

        try {
            // Create server socket and bind to port
            serverSocket = new ServerSocket(port);
            System.out.println("Server listening on port " + port);

            // Signal that server is ready for connections
            started.countDown();

            // Main server loop - continuously listens for incoming client connections.
            // Each accepted connection is handed off to the thread pool for processing.
            // If an exception occurs while the server is still running, log the error
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

    /**
     * Blocks until the server has started and is ready to accept connections
     * Used primarily for testing synchronization
     * @throws InterruptedException if interrupted while waiting
     */
    public void waitUntilStarted() throws InterruptedException {
        // block until countDown() called
        started.await();
    }

    /**
     * Handles connection
     * Parses HTTP request and routes to appropriate handler - GET or PUT
     *
     * @param socket The client socket connection
     */
    private void handleConnection(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // Read the HTTP request
            String requestLine = in.readLine();
            if (requestLine == null) return;

            // Parse HTTP headers into a map
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                String[] parts = line.split(": ", 2);
                if (parts.length == 2) {
                    headers.put(parts[0].toLowerCase(), parts[1]);
                }
            }

            // Parse request line components
            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 3) {
                sendResponse(out, 400, "Bad Request", "");
                return;
            }

            String method = requestParts[0];

            // Route request based on HTTP method
            if ("GET".equals(method)) {
                String path = requestParts[1];
                System.out.println("Path :: "+path);
                handleGetRequest(out, headers, socket.getRemoteSocketAddress().toString(), path);
            } else if ("PUT".equals(method)) {
                handlePutRequest(in, out, headers);
            } else {
                // Method not supported
                sendResponse(out, 400, "Bad Request", "");
            }

        } catch (IOException e) {
            System.err.println("Connection handling error: " + e.getMessage());
        }
    }

    /**
     * Handles HTTP GET requests from clients requesting weather data
     * Implements Lamport clock synchronization and optional station filtering
     *
     * @param out PrintWriter for sending response
     * @param headers HTTP headers from the request
     * @param clientId Unique identifier for the client
     * @param path Request path containing optional query parameters
     */
    private void handleGetRequest(PrintWriter out, Map<String, String> headers, String clientId, String path) {
        try {
            // Extract Lamport clock from headers (default to 0 if missing)
            int clockValue = Integer.parseInt(headers.getOrDefault("lamport-clock", "0"));

            // Parse query parameters for station ID filtering
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

            // Create request object for asynchronous processing
            Request request = new Request(Request.Type.GET, clientId, clockValue, stationId);

            // Add request to processing queue
            if (!requestQueue.offer(request)) {
                sendResponse(out, 503, "Service Unavailable", "Server is overloaded, try again later.");
                return;
            }

            // Wait for request processing to complete
            String result = request.waitForResult();

            // Send appropriate response based on processing result
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

    /**
     * Handles HTTP PUT requests from ContentServers uploading weather data
     * Implements Lamport clock validation and data persistence
     *
     * @param in BufferedReader for reading request body
     * @param out PrintWriter for sending response
     * @param headers HTTP headers from the request
     */
    private void handlePutRequest(BufferedReader in, PrintWriter out, Map<String, String> headers) {
        try {
            // Extract Lamport clock and content length from headers
            int clockValue = Integer.parseInt(headers.getOrDefault("lamport-clock", "0"));
            int contentLength = Integer.parseInt(headers.getOrDefault("content-length", "0"));

            // Handle empty request body
            if (contentLength == 0) {
                sendResponse(out, 204, "No Content", "");
                return;
            }

            // Read the JSON payload from request body
            char[] buffer = new char[contentLength];
            int totalRead = 0;
            while (totalRead < contentLength) {
                int read = in.read(buffer, totalRead, contentLength - totalRead);
                if (read == -1) break;
                totalRead += read;
            }

            String jsonData = new String(buffer, 0, totalRead);

            // Create request for asynchronous processing
            Request request = new Request(Request.Type.PUT, null, clockValue, jsonData);

            // Add to processing queue
            if (!requestQueue.offer(request)) {
                sendResponse(out, 503, "Service Unavailable", "Server is overloaded, try again later.");
                return;
            }

            // Wait for processing and send appropriate response
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

    /**
     * Background thread that continuously processes requests from the queue
     * Ensures requests are handled in FIFO order and provides thread safety
     */
    private void processRequests() {
        while (running) {
            try {
                // Block until a request is available
                Request request = requestQueue.take();

                // Process request based on type
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

    /**
     * Processes PUT requests by validating Lamport clocks and persisting weather data
     * Implements causal ordering through clock comparison
     *
     * @param request The PUT request containing weather data
     */
    private void processPutRequest(Request request) {
        try {
            // Deserialize weather data from JSON
            WeatherData weatherData = gson.fromJson(request.data, WeatherData.class);
            String stationId = weatherData.getId();

            // Validate that station ID is present
            if (stationId == null) {
                request.setResult("ERROR:400");
                return;
            }

            // Lamport clock validation - reject out-of-order updates
            int lastClock = contentServerClocks.getOrDefault(stationId, 0);
            if (request.clockValue <= lastClock) {
                request.setResult("ERROR:400");
                return;
            }

            // Update clock for this content server
            contentServerClocks.put(stationId, request.clockValue);

            // Set timestamp for expiry management
            weatherData.setLastUpdated(System.currentTimeMillis() / 1000);

            // Thread-safe file operations
            synchronized (fileLock) {
                // Load existing data, remove old entry for this station, add new data
                List<WeatherData> allData = FileUtils.loadWeatherData();
                allData.removeIf(d -> stationId.equals(d.getId()));
                allData.add(weatherData);
                FileUtils.saveWeatherData(allData);
            }

            // Return appropriate status code (201 for first time, 200 for updates)
            boolean isFirstTime = lastClock == 0;
            request.setResult(isFirstTime ? "201" : "200");
        } catch (Exception e) {
            request.setResult("ERROR:500");
        }
    }

    /**
     * Processes GET requests by validating Lamport clocks and returning weather data
     * Supports optional filtering by station ID
     *
     * @param request The GET request with optional station filter
     */
    private void processGetRequest(Request request) {
        try {
            int lastClock = clientClocks.getOrDefault(request.entityId, 0);
            if (request.clockValue <= lastClock) {
                request.setResult("ERROR:400");
                return;
            }

            // Update client's clock
            clientClocks.put(request.entityId, request.clockValue);

            // Thread-safe data retrieval and filtering
            synchronized (fileLock) {
                List<WeatherData> allData = FileUtils.loadWeatherData();

                // Remove expired data
                allData.removeIf(d -> d.isExpired(EXPIRY_SECONDS));

                // Apply station ID filter if specified
                String stationId = request.data; // optional
                List<WeatherData> filteredData;
                if (stationId == null || stationId.isEmpty()) {
                    filteredData = allData; // return all
                } else {
                    // Filter by specific station ID
                    filteredData = allData.stream()
                            .filter(d -> stationId.equals(d.getId()))
                            .collect(Collectors.toList());
                }

                // Serialize filtered data to JSON
                String jsonResult = gson.toJson(filteredData);
                request.setResult(jsonResult);
            }
        } catch (Exception e) {
            request.setResult("ERROR:500");
        }
    }

    /**
     * Background task that periodically removes expired weather data
     * Runs every 10 seconds to clean up expired data
     */
    private void manageExpiredData() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                synchronized (fileLock) {
                    List<WeatherData> allData = FileUtils.loadWeatherData();
                    List<String> expiredStations = new ArrayList<>();

                    // Identify and remove expired entries
                    allData.removeIf(d -> {
                        boolean expired = d.isExpired(EXPIRY_SECONDS);
                        if (expired) expiredStations.add(d.getId());
                        return expired;
                    });

                    // Save updated data and clean up clock tracking
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

    /**
     * Sends HTTP response to client
     *
     * @param out PrintWriter for response output
     * @param statusCode HTTP status code
     * @param statusMessage HTTP status message
     * @param body Response body content
     */
    private void sendResponse(PrintWriter out, int statusCode, String statusMessage, String body) {
        out.println("HTTP/1.1 " + statusCode + " " + statusMessage);
        out.println("Content-Type: application/json");
        out.println("Content-Length: " + body.length());
        out.println();
        out.print(body);
        out.flush();
    }

    /**
     * Maps HTTP status codes to standard messages
     *
     * @param statusCode The HTTP status code
     * @return Standard status message for the code
     */
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

    /**
     * Inner class representing an asynchronous request
     * Uses CountDownLatch for thread-safe result waiting
     */
    private static class Request {
        enum Type { GET, PUT }
        final Type type;                    // Request type (GET or PUT)
        final String entityId;              // Client/server identifier
        final int clockValue;               // Lamport clock value
        final String data;                  // Request payload or filter parameter


        // Synchronization primitive for asynchronous result handling
        private final CountDownLatch latch = new CountDownLatch(1);
        // Thread-safe result storage
        private volatile String result;

        /**
         * Creates a new request
         *
         * @param type The request type (GET or PUT)
         * @param entityId Unique identifier for the requesting entity
         * @param clockValue Lamport clock value for ordering
         * @param data Request data (JSON for PUT, station filter for GET)
         */
        Request(Type type, String entityId, int clockValue, String data) {
            this.type = type;
            this.entityId = entityId;
            this.clockValue = clockValue;
            this.data = data;
        }

        /**
         * Sets the result and signals waiting threads
         *
         * @param result The processing result
         */
        void setResult(String result) {
            this.result = result;
            latch.countDown();
        }

        /**
         * Blocks until result is available
         *
         * @return The processing result
         * @throws InterruptedException if interrupted while waiting
         */
        String waitForResult() throws InterruptedException {
            latch.await();
            return result;
        }
    }

    /**
     * Gracefully stops the server and cleans up resources
     * Shuts down thread pools, closes sockets, and clears data structures
     */
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

