import com.weather.AggregationServer;
import com.weather.ContentServer;
import com.weather.GETClient;
import org.junit.Test;
import utils.FileUtils;
import utils.WeatherData;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class GETClientTest {

    @Test
    public void testing() throws InterruptedException {

        ContentServer cs1 = new ContentServer("localhost:4567", "src/main/resources/cs_weather_data.txt");
        cs1.start();
        assertEquals(1, cs1.getLamportClock());

        Thread.sleep(1000);
        cs1.start();

        assertEquals(2, cs1.getLamportClock());
    }

    @Test
    public void getData() throws InterruptedException {

        AggregationServer ag = new AggregationServer();
        Thread t1 = new Thread(ag::start);
        t1.start();


        ContentServer cs1 = new ContentServer("localhost:8080", "src/main/resources/nsw/weather_data_1.txt");
        Thread t2 = new Thread(cs1::start);
        t2.start();

        Thread t3 = new Thread(() -> cs1.start("src/main/resources/sa/weather_data_1.txt"));
        t3.start();

        t2.join();
        t3.join();
        WeatherData wd1 = FileUtils.loadWeatherDataObj("src/main/resources/nsw/weather_data_1.txt");
        WeatherData wd2 = FileUtils.loadWeatherDataObj("src/main/resources/sa/weather_data_1.txt");
        Set<WeatherData> set1 = new HashSet<>(Arrays.asList(wd1, wd2));
        System.out.println("=========== Weather Data Sent for Update =========");
        System.out.println(set1);

        List< WeatherData> list2 = new GETClient("http://localhost:8080").fetchWeatherData(null);
        Set<WeatherData> set2 = new HashSet<>(list2);
        assertEquals(set1,set2);
        ag.stop();
        cs1.cleanup();
    }
}
