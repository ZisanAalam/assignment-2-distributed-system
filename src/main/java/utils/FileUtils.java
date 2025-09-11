package utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileUtils {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final String ROOT_DIR = "src/main/resources/";
    private static final String DATA_FILE = ROOT_DIR + "weather_data.json";
    private static final String TEMP_FILE = ROOT_DIR + "weather_data.tmp";

    public static String readCSWeatherFile(String filePath) {
        try {
            if (!Files.exists(Paths.get(filePath))) {
                System.err.println("Weather data file not found: " + filePath);
                return null;
            }
            return Files.readString(Paths.get(filePath));
        } catch (IOException e) {
            System.err.println("Error reading weather file: " + e.getMessage());
            return null;
        }
    }

    public static WeatherData parseWeatherData(String content) {
        WeatherData weatherData = new WeatherData();
        Map<String, String> dataMap = new HashMap<>();

        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split(":", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                dataMap.put(key, value);
            }
        }

        try {
            weatherData.setId(dataMap.get("id"));
            weatherData.setName(dataMap.get("name"));
            weatherData.setState(dataMap.get("state"));
            weatherData.setTimeZone(dataMap.get("time_zone"));

            if (dataMap.get("lat") != null) {
                weatherData.setLat(Double.parseDouble(dataMap.get("lat")));
            }
            if (dataMap.get("lon") != null) {
                weatherData.setLon(Double.parseDouble(dataMap.get("lon")));
            }

            weatherData.setLocalDateTime(dataMap.get("local_date_time"));
            weatherData.setLocalDateTimeFull(dataMap.get("local_date_time_full"));

            if (dataMap.get("air_temp") != null) {
                weatherData.setAirTemp(Double.parseDouble(dataMap.get("air_temp")));
            }
            if (dataMap.get("apparent_t") != null) {
                weatherData.setApparentT(Double.parseDouble(dataMap.get("apparent_t")));
            }

            weatherData.setCloud(dataMap.get("cloud"));

            if (dataMap.get("dewpt") != null) {
                weatherData.setDewpt(Double.parseDouble(dataMap.get("dewpt")));
            }
            if (dataMap.get("press") != null) {
                weatherData.setPress(Double.parseDouble(dataMap.get("press")));
            }
            if (dataMap.get("rel_hum") != null) {
                weatherData.setRelHum(Integer.parseInt(dataMap.get("rel_hum")));
            }

            weatherData.setWindDir(dataMap.get("wind_dir"));

            if (dataMap.get("wind_spd_kmh") != null) {
                weatherData.setWindSpeedKmh(Integer.parseInt(dataMap.get("wind_spd_kmh")));
            }
            if (dataMap.get("wind_spd_kt") != null) {
                weatherData.setWindSpeedKt(Integer.parseInt(dataMap.get("wind_spd_kt")));
            }

            if (weatherData.getId() == null || weatherData.getId().isEmpty()) {
                System.err.println("Weather data missing required 'id' field");
                return null;
            }

            return weatherData;

        } catch (NumberFormatException e) {
            System.err.println("Error parsing numeric fields in weather data: " + e.getMessage());
            return null;
        }
    }

    public static void saveWeatherData(List<WeatherData> data) throws IOException {
        String jsonData = gson.toJson(data);

        Path tempPath = Paths.get(TEMP_FILE);
        Path dataPath = Paths.get(DATA_FILE);

        // Write atomically: first temp, then move
        Files.writeString(tempPath, jsonData);
        Files.move(tempPath, dataPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static List<WeatherData> loadWeatherData() {
        try {
            if (!Files.exists(Paths.get(DATA_FILE))) return new ArrayList<>();
            String content = Files.readString(Paths.get(DATA_FILE));
            if (content.trim().isEmpty()) return new ArrayList<>();
            return gson.fromJson(content, new TypeToken<List<WeatherData>>() {}.getType());
        } catch (Exception e) {
            System.err.println("Error loading weather data: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static void deleteDataFile() {
        File file = new File(DATA_FILE);
        if (file.exists()) {
            if (file.delete()) {
                System.out.println("Deleted file: " + DATA_FILE);
            } else {
                System.out.println("Failed to delete file: " + DATA_FILE);
            }
        } else {
            System.out.println("File does not exist: " + DATA_FILE);
        }
    }

    public static WeatherData loadWeatherDataObj(String filePath) {
        //Read file content
        String fileContent = readCSWeatherFile(filePath);
        if (fileContent == null) {
            System.err.println("Failed to read weather data from file: " + filePath);
            return null;
        }

        // Parse content into WeatherData object
        WeatherData weatherData = parseWeatherData(fileContent);
        if (weatherData == null) {
            System.err.println("Failed to parse weather data from file: " + filePath);
        }

        return weatherData;
    }
}
