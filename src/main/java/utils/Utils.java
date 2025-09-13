package utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.util.List;

public class Utils {

    public static List<WeatherData> toWeatherDataList(String jsonData){
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<WeatherData> weatherDataList = gson.fromJson(jsonData, new TypeToken<List<WeatherData>>() {}.getType());
        return weatherDataList;
    }

    public static String toPretty(String jsonData){
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonElement je = JsonParser.parseString(jsonData);
        return gson.toJson(je);
    }
}
