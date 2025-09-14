import com.weather.AggregationServer;
import com.weather.ContentServer;
import com.weather.GETClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class BasicRESTFunctionalityTest {

    private AggregationServer ag;
    private final int serverPort = 8080;
    private final String serverUrl = "http://localhost:"+serverPort;

    @Before
    public void setUp() throws Exception {
        ag = new AggregationServer();
        Thread t1 = new Thread(ag::start);
        t1.start();
        ag.waitUntilStarted();
    }

    @After
    public void tearDown() {
        if (ag != null) {
            ag.stop();
        }
    }

    @Test
    public void test01_GetDataWithoutUpdate(){
        String jsonData = new GETClient(serverUrl).fetchWeatherData(null);
        assertNotNull("❌ Expected empty list from server", jsonData);
        System.out.println("✅ Test Passed. Data received: " + jsonData);
    }

    @Test
    public void test02_OneUpdateOfStation(){
        ContentServer cs = new ContentServer(serverUrl, "src/main/resources/nsw/weather_data_1.txt");
        int status = cs.start();
        assertEquals(201, status);
        System.out.println("✅ Test Passed: Weather data successfully loaded for the first time with status " + status);
    }

    @Test
    public void test03_TwoUpdatesForSameStation(){
        // Load first station data
        ContentServer cs = new ContentServer(serverUrl, "src/main/resources/nsw/weather_data_1.txt");
        int status1 = cs.start();
        assertEquals(201, status1);
        System.out.println("✅ Station data first loaded successfully with status " + status1);

        // Load second data from same station
        int status2 = cs.start("src/main/resources/nsw/weather_data_2.txt");
        assertEquals(200, status2);
        System.out.println("✅ Station data second loaded successfully with status " + status2);
        cs.cleanup();
    }

    @Test
    public void test03_updateThreeDataForSameStation(){
        // Load first station data
        ContentServer cs = new ContentServer(serverUrl, "src/main/resources/vic/weather_data_1.txt");
        int status1 = cs.start();
        assertEquals(201, status1);
        System.out.println("✅ Station data first loaded successfully with status " + status1);

        // Load second data from same station
        int status2 = cs.start("src/main/resources/vic/weather_data_2.txt");
        assertEquals(200, status2);
        System.out.println("✅ Station data second loaded successfully with status " + status2);

        // Load second data from same station
        int status3 = cs.start("src/main/resources/vic/weather_data_3.txt");
        assertEquals(200, status3);
        System.out.println("✅ Station data third loaded successfully with status " + status2);
        cs.cleanup();
    }

    @Test
    public void test04_UpdateOfTwoDifferentStation() {

        // Load first station data
        ContentServer cs1 = new ContentServer(serverUrl, "src/main/resources/nsw/weather_data_1.txt");
        int status1 = cs1.start();
        assertEquals(201, status1);
        System.out.println("✅ Station 1 data loaded successfully with status " + status1);

        // Load second station data
        ContentServer cs2 = new ContentServer(serverUrl, "src/main/resources/vic/weather_data_2.txt");
        int status2 = cs2.start();
        assertEquals(201, status2);
        System.out.println("✅ Station 2 data loaded successfully with status " + status2);
        cs1.cleanup();
        cs2.cleanup();
    }

    @Test
    public void test05_ThreeUpdatesForSameStation_OutOfClockOrder(){
        // Load first station data
        ContentServer cs = new ContentServer(serverUrl, "src/main/resources/vic/weather_data_1.txt");
        int status1 = cs.start();
        assertEquals(201, status1);
        System.out.println("✅ Station data first loaded successfully with status " + status1);

        // Load second data from same station
        int status2 = cs.start("src/main/resources/vic/weather_data_2.txt");
        assertEquals(200, status2);
        System.out.println("✅ Station data second loaded successfully with status " + status2);

        // Load second data from same station
        //reduce the clock value to make it out of order
        cs.reduceLamportClock();
        int status3 = cs.start("src/main/resources/vic/weather_data_3.txt");
        assertEquals(400, status3);
        System.out.println("✅ Station data third rejected with status " + status2);

        cs.cleanup();
    }

    @Test
    public void test06_InvalidMethodRequest() throws Exception {
        // Connect with raw socket and send POST request
        try (Socket socket = new Socket("localhost", serverPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Send POST request
            out.println("POST /weather.json HTTP/1.1");
            out.println("Host: "+serverUrl);
            out.println("User-Agent: TestClient/1.0");
            out.println("Content-Length: 0");
            out.println();
            out.flush();

            // Read response
            String responseLine = in.readLine();
            assertNotNull(responseLine);
            System.out.println("Response: " + responseLine);

            // Assert status code = 400
            assertTrue(responseLine.contains("400"));
            System.out.println("✅ Passed: Invalid method correctly returned 400");

        }
    }

    @Test
    public void test07_MalformedJsonRequest() throws Exception {
        try (Socket socket = new Socket("localhost", serverPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            String badJson = "{ invalid_json: true "; // deliberately malformed

            out.println("PUT /weather.json HTTP/1.1");
            out.println("Host: "+serverUrl);
            out.println("Content-Type: application/json");
            out.println("Content-Length: " + badJson.length());
            out.println("Lamport-Clock: 1");
            out.println();
            out.print(badJson);
            out.flush();

            String responseLine = in.readLine();
            assertNotNull(responseLine);
            System.out.println("Response (Malformed JSON): " + responseLine);

            assertTrue(responseLine.contains("500"));
            System.out.println("✅ Passed: Malformed JSON correctly returned 500");
        }


    }

    @Test
    public void test08_EmptyContent() throws Exception {
        try (Socket socket = new Socket("localhost", serverPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("PUT /weather.json HTTP/1.1");
            out.println("Host: "+serverUrl);
            out.println("Content-Type: application/json");
            out.println("Content-Length: 0"); // empty body
            out.println("Lamport-Clock: 1");
            out.println();
            out.flush();

            String responseLine = in.readLine();
            assertNotNull(responseLine);
            System.out.println("Response (Empty Content): " + responseLine);

            assertTrue(responseLine.contains("204"));
            System.out.println("✅ Passed: Empty content correctly returned 204");
        }


    }
}
