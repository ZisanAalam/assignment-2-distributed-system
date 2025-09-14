import com.weather.AggregationServer;
import com.weather.ContentServer;
import com.weather.GETClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import utils.FileUtils;
import utils.Utils;
import utils.WeatherData;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class ComprehensiveTest {

    private AggregationServer ag;
    private final int serverPort = 8080;
    private final String serverUrl = "http://localhost:"+serverPort;

    @Before
    public void ListUp() throws Exception {
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

    //PUT -> GET
    @Test
    public void test01_SingleUpdateAndGet()  {
        ContentServer cs = new ContentServer(serverUrl, "src/main/resources/nsw/weather_data_1.txt");
        cs.start();

        WeatherData wd1 = FileUtils.loadWeatherDataObj("src/main/resources/nsw/weather_data_1.txt");
        List<WeatherData> list1 = Arrays.asList(wd1);

        String jsonData = new GETClient(serverUrl).fetchWeatherData(null);
        List<WeatherData> list2 = Utils.toWeatherDataList(jsonData);
        assertEquals(list1,list2);
        System.out.println("✅Passed: Station data first updated successful");
        cs.cleanup();
    }

    //PUT -> PUT -> GET
    @Test
    public void test02_TwoUpdateFromSameStation()  {
        ContentServer cs = new ContentServer(serverUrl, "src/main/resources/vic/weather_data_1.txt");
        cs.start();
        cs.start("src/main/resources/vic/weather_data_2.txt");

        WeatherData wd1 = FileUtils.loadWeatherDataObj("src/main/resources/vic/weather_data_2.txt");
        List<WeatherData> list1 = Arrays.asList(wd1);

        String jsonData = new GETClient(serverUrl).fetchWeatherData(null);
        List<WeatherData> List2 = Utils.toWeatherDataList(jsonData);
        assertEquals(list1,List2);
        System.out.println("✅Passed: Station data two consecutive updated successful");
        cs.cleanup();
    }

    //PUT  -> GET -> PUT
    @Test
    public void test03_UpdateGetUpdateFromSameStation()  {
        ContentServer cs = new ContentServer(serverUrl, "src/main/resources/vic/weather_data_1.txt");
        cs.start();

        WeatherData wd1 = FileUtils.loadWeatherDataObj("src/main/resources/vic/weather_data_1.txt");
        List<WeatherData> list1 = Arrays.asList(wd1);

        String jsonData = new GETClient(serverUrl).fetchWeatherData(null);
        List<WeatherData> List2 = Utils.toWeatherDataList(jsonData);
        assertEquals(list1,List2);

        cs.start("src/main/resources/vic/weather_data_2.txt");
        System.out.println("✅Passed: Update and Get working Sequentially in the order it is received ");
        cs.cleanup();
    }

    // PUT (Queensland station) -> PUT (Tasmania Station) -> GET
    @Test
    public void test04_TwoUpdateFromDifferentStation()  {
        ContentServer cs1 = new ContentServer(serverUrl, "src/main/resources/tasmania/weather_data_3.txt");
        cs1.start();
        
        ContentServer cs2 = new ContentServer(serverUrl, "src/main/resources/queensland/weather_data_2.txt");
        cs2.start();

        WeatherData wd1 = FileUtils.loadWeatherDataObj("src/main/resources/tasmania/weather_data_3.txt");
        WeatherData wd2 = FileUtils.loadWeatherDataObj("src/main/resources/queensland/weather_data_2.txt");
        List<WeatherData> list1 = Arrays.asList(wd1, wd2);

        String jsonData = new GETClient(serverUrl).fetchWeatherData(null);
        List<WeatherData> List2 = Utils.toWeatherDataList(jsonData);
        assertEquals(list1,List2);
        System.out.println("✅Passed: Both Station data update successful");
        cs1.cleanup();
    }

    @Test
    public void test05_FetchByServerId() {
        ContentServer cs1 = new ContentServer(serverUrl, "src/main/resources/sa/weather_data_2.txt");
        cs1.start();

        ContentServer cs2 = new ContentServer(serverUrl, "src/main/resources/queensland/weather_data_3.txt");
        cs2.start();

        WeatherData saWD = FileUtils.loadWeatherDataObj("src/main/resources/sa/weather_data_2.txt");
        WeatherData qldWD = FileUtils.loadWeatherDataObj("src/main/resources/queensland/weather_data_3.txt");

        List<WeatherData> saList1 = Arrays.asList(saWD);
        List<WeatherData> qldList1 = Arrays.asList(qldWD);

        GETClient client = new GETClient(serverUrl);
        String saJsonData = client.fetchWeatherData("SA01");
        List<WeatherData> saList2 = Utils.toWeatherDataList(saJsonData);
        assertEquals(saList1, saList2);

        String qldJsonData = client.fetchWeatherData("QLD01");
        List<WeatherData> qldList2 = Utils.toWeatherDataList(qldJsonData);
        assertEquals(qldList1, qldList2);

        String randomJsonData = client.fetchWeatherData("randomID");
        assertNotNull("❌ Expected empty list from server", randomJsonData);

        System.out.println("✅Passed: Data fetched by Station ID Verified");
    }

    @Test
    public void test06_RapidUpdateLamportClockSync() {
        ContentServer cs = new ContentServer(serverUrl, "src/main/resources/vic/weather_data_1.txt");
        for(int index=0; index<10; index++){
            cs.start("src/main/resources/vic/weather_data_2.txt");
        }

        for(int index=0; index<10; index++){
            cs.start("src/main/resources/vic/weather_data_3.txt");
        }

        assertEquals(20, cs.getLamportClock());
        System.out.println("✅Passed: Lamport Clock in sync during rapid update");
        cs.cleanup();
    }

    @Test
    public void test07_30SecondDataExpiration() throws InterruptedException {
        ContentServer cs1 = new ContentServer(serverUrl, "src/main/resources/wa/weather_data_2.txt");
        cs1.start();

        System.out.println("Sleep Thread for 35 second to let data expire");
        Thread.sleep(35000);
        ContentServer cs2 = new ContentServer(serverUrl, "src/main/resources/sa/weather_data_3.txt");
        cs2.start();


        WeatherData wd1 = FileUtils.loadWeatherDataObj("src/main/resources/sa/weather_data_3.txt");
        List<WeatherData> list1 = Arrays.asList(wd1);

        String jsonData = new GETClient(serverUrl).fetchWeatherData(null);
        List<WeatherData> list2 = Utils.toWeatherDataList(jsonData);
        assertEquals(list1,list2);
        System.out.println("✅Passed: Expired Data has been removed successfully after 30 second");

        cs1.cleanup();
        cs2.cleanup();
    }
}
