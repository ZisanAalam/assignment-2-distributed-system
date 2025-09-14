package utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.util.Objects;

/**
 * WeatherData represents a complete weather station record in the distributed system.
 * This class serves as the primary data model for weather information, providing
 * JSON serialization/deserialization capabilities and data validation methods.
 * Key Features:
 * - Complete weather station metadata (location, timezone, identifiers)
 * - Comprehensive weather measurements (temperature, pressure, humidity, wind)
 * - JSON serialization with custom field mapping using @SerializedName
 * - Data expiry functionality for automatic cleanup of stale data
 * - Thread-safe timestamp management for distributed synchronization
 * - Proper equals/hashCode implementation for collection operations
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
    private double apparentT;
    private String cloud;
    private double dewpt;
    private double press;
    @SerializedName("rel_hum")
    private int relHum;
    @SerializedName("wind_dir")
    private String windDir;
    @SerializedName("wind_spd_kmh")
    private int windSpeedKmh;
    @SerializedName("wind_spd_kt")
    private int windSpeedKt;
    @SerializedName("_last_updated")
    private long lastUpdated;

    // Constructors
    public WeatherData() {}


    /**
     * Converts this WeatherData object to pretty-formatted JSON string
     *
     * @return JSON representation of this weather data
     */
    public String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }

    /**
     * Creates WeatherData object from JSON string
     *
     * @param json JSON string representation of weather data
     * @return WeatherData object parsed from JSON
     */
    public static WeatherData fromJson(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, WeatherData.class);
    }

    /**
     * Checks if this weather data has expired based on specified timeout
     * @param expirySeconds Maximum age in seconds before data is considered expired
     * @return true if data is expired, false otherwise
     */
    public boolean isExpired(int expirySeconds) {
        long currentTime = System.currentTimeMillis() / 1000;
        return (currentTime - lastUpdated) > expirySeconds;
    }

    public void updateTimestamp() {
        this.lastUpdated = System.currentTimeMillis();
    }

    // Getter and Setter methods for all fields

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

    public void setLocalDateTime(String localDateTime) { this.localDateTime = localDateTime; }

    public void setLocalDateTimeFull(String localDateTimeFull) { this.localDateTimeFull = localDateTimeFull; }

    public double getAirTemp() { return airTemp; }
    public void setAirTemp(double airTemp) { this.airTemp = airTemp; }

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

    public int getWindSpeedKmh() { return windSpeedKmh; }
    public void setWindSpeedKmh(int windSpeedKmh) { this.windSpeedKmh = windSpeedKmh; }

    public int getWindSpeedKt() { return windSpeedKt; }
    public void setWindSpeedKt(int windSpeedKt) { this.windSpeedKt = windSpeedKt; }

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

    /**
     * Compares this WeatherData object with another for equality
     *
     * @param o Object to compare with
     * @return true if objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WeatherData that = (WeatherData) o;
        return Double.compare(that.lat, lat) == 0 &&
                Double.compare(that.lon, lon) == 0 &&
                Double.compare(that.airTemp, airTemp) == 0 &&
                Double.compare(that.apparentT, apparentT) == 0 &&
                relHum == that.relHum &&
                Double.compare(that.press, press) == 0 &&
                windSpeedKmh == that.windSpeedKmh &&
                windSpeedKt == that.windSpeedKt &&
                Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(state, that.state) &&
                Objects.equals(timeZone, that.timeZone) &&
                Objects.equals(localDateTime, that.localDateTime) &&
                Objects.equals(localDateTimeFull, that.localDateTimeFull) &&
                Objects.equals(cloud, that.cloud) &&
                Objects.equals(windDir, that.windDir);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, state, timeZone, lat, lon, localDateTime,
                localDateTimeFull, airTemp, apparentT, cloud, dewpt, press,
                relHum, windDir, windSpeedKmh, windSpeedKt);
    }

}