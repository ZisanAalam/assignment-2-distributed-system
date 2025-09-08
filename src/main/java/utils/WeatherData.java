package utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

/**
 * Weather data model for JSON serialization/deserialization
 */

public class WeatherData {
    private String id;
    private String name;
    private String state;
    @SerializedName("time_zone")
    private String timeZone;
    private double lat;
    private double lon;
    @SerializedName("local_date_time")
    private String localDateTime;
    @SerializedName("local_date_time_full")
    private String localDateTimeFull;
    @SerializedName("air_temp")
    private double airTemp;
    @SerializedName("apparent_t")
    private double apparentT; // Fixed: changed from apparentTemp to match JSON
    private String cloud;
    private double dewpt;
    private double press;
    @SerializedName("rel_hum")
    private int relHum;
    @SerializedName("wind_dir")
    private String windDir;
    @SerializedName("wind_spd_kmh")
    private int windSpeedKmh; // Fixed: changed from windSpdKmh
    @SerializedName("wind_spd_kt")
    private int windSpeedKt; // Fixed: changed from windSpdKt

    // Internal fields for server management
    @SerializedName("_content_server_id")
    private String contentServerId;
    @SerializedName("_last_updated")
    private long lastUpdated;

    // Constructors
    public WeatherData() {}

    public WeatherData(String id) {
        this.id = id;
        this.lastUpdated = System.currentTimeMillis();
    }

    // JSON serialization methods
    public String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }

    public static WeatherData fromJson(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, WeatherData.class);
    }

    // Utility methods
    public boolean isExpired(long currentTime, int expirySeconds) {
        return (currentTime - lastUpdated) > (expirySeconds * 1000L);
    }

    public void updateTimestamp() {
        this.lastUpdated = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }

    public String getLocalDateTime() { return localDateTime; }
    public void setLocalDateTime(String localDateTime) { this.localDateTime = localDateTime; }

    public String getLocalDateTimeFull() { return localDateTimeFull; }
    public void setLocalDateTimeFull(String localDateTimeFull) { this.localDateTimeFull = localDateTimeFull; }

    public double getAirTemp() { return airTemp; }
    public void setAirTemp(double airTemp) { this.airTemp = airTemp; }

    // Fixed: changed method names to match field names
    public double getApparentT() { return apparentT; }
    public void setApparentT(double apparentT) { this.apparentT = apparentT; }

    public String getCloud() { return cloud; }
    public void setCloud(String cloud) { this.cloud = cloud; }

    public double getDewpt() { return dewpt; }
    public void setDewpt(double dewpt) { this.dewpt = dewpt; }

    public double getPress() { return press; }
    public void setPress(double press) { this.press = press; }

    public int getRelHum() { return relHum; }
    public void setRelHum(int relHum) { this.relHum = relHum; }

    public String getWindDir() { return windDir; }
    public void setWindDir(String windDir) { this.windDir = windDir; }

    // Fixed: changed method names to match field names
    public int getWindSpeedKmh() { return windSpeedKmh; }
    public void setWindSpeedKmh(int windSpeedKmh) { this.windSpeedKmh = windSpeedKmh; }

    public int getWindSpeedKt() { return windSpeedKt; }
    public void setWindSpeedKt(int windSpeedKt) { this.windSpeedKt = windSpeedKt; }

    public String getContentServerId() { return contentServerId; }
    public void setContentServerId(String contentServerId) { this.contentServerId = contentServerId; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }

    @Override
    public String toString() {
        return "WeatherData{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", airTemp=" + airTemp +
                ", apparentT=" + apparentT +
                ", relHum=" + relHum +
                ", press=" + press +
                ", windDir='" + windDir + '\'' +
                ", windSpeedKmh=" + windSpeedKmh +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}

//public class WeatherData {
//    private String id;
//    private String name;
//    private String state;
//    @SerializedName("time_zone")
//    private String timeZone;
//    private double lat;
//    private double lon;
//    @SerializedName("local_date_time")
//    private String localDateTime;
//    @SerializedName("local_date_time_full")
//    private String localDateTimeFull;
//    @SerializedName("air_temp")
//    private double airTemp;
//    @SerializedName("apparent_t")
//    private double apparentTemp;
//    private String cloud;
//    private double dewpt;
//    private double press;
//    @SerializedName("rel_hum")
//    private int relHum;
//    @SerializedName("wind_dir")
//    private String windDir;
//    @SerializedName("wind_spd_kmh")
//    private int windSpdKmh;
//    @SerializedName("wind_spd_kt")
//    private int windSpdKt;
//
//    // Internal fields for server management - serialized name intentionally uses underscore
//    @SerializedName("_content_server_id")
//    private String contentServerId;
//    @SerializedName("_last_updated")
//    private long lastUpdated;
//
//    // Constructors
//    public WeatherData() {}
//
//    public WeatherData(String id) {
//        this.id = id;
//        this.lastUpdated = System.currentTimeMillis();
//    }
//
//    // JSON serialization methods
//    public String toJson() {
//        Gson gson = new GsonBuilder().setPrettyPrinting().create();
//        return gson.toJson(this);
//    }
//
//    public static WeatherData fromJson(String json) {
//        Gson gson = new Gson();
//        return gson.fromJson(json, WeatherData.class);
//    }
//
//    // Utility methods
//    public boolean isExpired(long currentTime, int expirySeconds) {
//        return (currentTime - lastUpdated) > (expirySeconds * 1000L);
//    }
//
//    public void updateTimestamp() {
//        this.lastUpdated = System.currentTimeMillis();
//    }
//
//    // Getters and Setters
//    public String getId() { return id; }
//    public void setId(String id) { this.id = id; }
//
//    public String getName() { return name; }
//    public void setName(String name) { this.name = name; }
//
//    public String getState() { return state; }
//    public void setState(String state) { this.state = state; }
//
//    public String getTimeZone() { return timeZone; }
//    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
//
//    public double getLat() { return lat; }
//    public void setLat(double lat) { this.lat = lat; }
//
//    public double getLon() { return lon; }
//    public void setLon(double lon) { this.lon = lon; }
//
//    public String getLocalDateTime() { return localDateTime; }
//    public void setLocalDateTime(String localDateTime) { this.localDateTime = localDateTime; }
//
//    public String getLocalDateTimeFull() { return localDateTimeFull; }
//    public void setLocalDateTimeFull(String localDateTimeFull) { this.localDateTimeFull = localDateTimeFull; }
//
//    public double getAirTemp() { return airTemp; }
//    public void setAirTemp(double airTemp) { this.airTemp = airTemp; }
//
//    public double getApparentTemp() { return apparentTemp; }
//    public void setApparentTemp(double apparentTemp) { this.apparentTemp = apparentTemp; }
//
//    public String getCloud() { return cloud; }
//    public void setCloud(String cloud) { this.cloud = cloud; }
//
//    public double getDewpt() { return dewpt; }
//    public void setDewpt(double dewpt) { this.dewpt = dewpt; }
//
//    public double getPress() { return press; }
//    public void setPress(double press) { this.press = press; }
//
//    public int getRelHum() { return relHum; }
//    public void setRelHum(int relHum) { this.relHum = relHum; }
//
//    public String getWindDir() { return windDir; }
//    public void setWindDir(String windDir) { this.windDir = windDir; }
//
//    public int getWindSpdKmh() { return windSpdKmh; }
//    public void setWindSpdKmh(int windSpdKmh) { this.windSpdKmh = windSpdKmh; }
//
//    public int getWindSpdKt() { return windSpdKt; }
//    public void setWindSpdKt(int windSpdKt) { this.windSpdKt = windSpdKt; }
//
//    public String getContentServerId() { return contentServerId; }
//    public void setContentServerId(String contentServerId) { this.contentServerId = contentServerId; }
//
//    public long getLastUpdated() { return lastUpdated; }
//    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
//}