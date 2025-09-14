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

/**
 * FileUtils provides utility methods for file operations a
 * Key Features:
 * - Weather data parsing with error handling
 * - Atomic file operations for thread safety
 * - JSON serialization/deserialization for data persistence
 * - Weather data validation and field mapping
 * - Temporary file usage to prevent corruption
 */
public class FileUtils {

    // Gson instance with pretty printing for readable JSON output
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // File system paths for data storage
    private static final String ROOT_DIR = "src/main/resources/";
    private static final String DATA_FILE = ROOT_DIR + "weather_data.json";
    private static final String TEMP_FILE = ROOT_DIR + "weather_data.tmp";

    /**
     * Reads weather data text file
     * Handles various file system errors and validates file existence
     *
     * @param filePath Path to the weather data file to read
     * @return File content as string, null if file cannot be read
     */
    public static String readCSWeatherFile(String filePath) {
        try {
            // Check if file exists before attempting to read
            if (!Files.exists(Paths.get(filePath))) {
                System.err.println("Weather data file not found: " + filePath);
                return null;
            }
            // Read entire file content into string
            return Files.readString(Paths.get(filePath));
        } catch (IOException e) {
            System.err.println("Error reading weather file: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parses text weather data string into a WeatherData object
     *
     * @param content content string with key:value pairs
     * @return Parsed WeatherData object, null if parsing fails
     */
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
            // Map string fields directly
            weatherData.setId(dataMap.get("id"));
            weatherData.setName(dataMap.get("name"));
            weatherData.setState(dataMap.get("state"));
            weatherData.setTimeZone(dataMap.get("time_zone"));

            // Parse numeric fields with null checking
            if (dataMap.get("lat") != null) {
                weatherData.setLat(Double.parseDouble(dataMap.get("lat")));
            }
            if (dataMap.get("lon") != null) {
                weatherData.setLon(Double.parseDouble(dataMap.get("lon")));
            }

            // Set date/time fields
            weatherData.setLocalDateTime(dataMap.get("local_date_time"));
            weatherData.setLocalDateTimeFull(dataMap.get("local_date_time_full"));

            // Parse temperature and weather condition fields
            if (dataMap.get("air_temp") != null) {
                weatherData.setAirTemp(Double.parseDouble(dataMap.get("air_temp")));
            }
            if (dataMap.get("apparent_t") != null) {
                weatherData.setApparentT(Double.parseDouble(dataMap.get("apparent_t")));
            }

            weatherData.setCloud(dataMap.get("cloud"));

            // Parse pressure and humidity fields
            if (dataMap.get("dewpt") != null) {
                weatherData.setDewpt(Double.parseDouble(dataMap.get("dewpt")));
            }
            if (dataMap.get("press") != null) {
                weatherData.setPress(Double.parseDouble(dataMap.get("press")));
            }
            if (dataMap.get("rel_hum") != null) {
                weatherData.setRelHum(Integer.parseInt(dataMap.get("rel_hum")));
            }

            // Set wind direction as string
            weatherData.setWindDir(dataMap.get("wind_dir"));

            // Parse wind speed fields
            if (dataMap.get("wind_spd_kmh") != null) {
                weatherData.setWindSpeedKmh(Integer.parseInt(dataMap.get("wind_spd_kmh")));
            }
            if (dataMap.get("wind_spd_kt") != null) {
                weatherData.setWindSpeedKt(Integer.parseInt(dataMap.get("wind_spd_kt")));
            }

            // Validate that required ID field is present
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

    /**
     * Saves weather data list to persistent JSON storage using atomic file operations
     * Uses temporary file and atomic move to prevent data corruption during writes
     *
     * @param data List of WeatherData objects to persist
     * @throws IOException If file operations fail
     */
    public static void saveWeatherData(List<WeatherData> data) throws IOException {
        // Convert data to JSON format
        String jsonData = gson.toJson(data);

        Path tempPath = Paths.get(TEMP_FILE);
        Path dataPath = Paths.get(DATA_FILE);

        // Atomic write operation: write to temp file first, then move
        // This prevents corruption if write operation is interrupted
        Files.writeString(tempPath, jsonData);
        Files.move(tempPath, dataPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Loads weather data from persistent JSON storage
     * Creates empty list if file doesn't exist or is corrupted
     *
     * @return List of WeatherData objects, empty list if file doesn't exist or error occurs
     */
    public static List<WeatherData> loadWeatherData() {
        try {
            // Return empty list if data file doesn't exist
            if (!Files.exists(Paths.get(DATA_FILE))) return new ArrayList<>();

            // Read file content
            String content = Files.readString(Paths.get(DATA_FILE));
            if (content.trim().isEmpty()) return new ArrayList<>();

            // Deserialize JSON to List<WeatherData>
            return gson.fromJson(content, new TypeToken<List<WeatherData>>() {}.getType());
        } catch (Exception e) {
            System.err.println("Error loading weather data: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Deletes the persistent weather data file
     * Used during server cleanup to remove stale data
     */
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

    /**
     * Method to load a single WeatherData object from file
     *
     * @param filePath Path to weather data file
     * @return Parsed WeatherData object, null if error occurs
     */
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
