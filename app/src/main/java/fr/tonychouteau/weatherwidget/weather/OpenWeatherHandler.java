package fr.tonychouteau.weatherwidget.weather;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.function.Consumer;

import fr.tonychouteau.weatherwidget.remote.http.AsynchJsonHandler;
import fr.tonychouteau.weatherwidget.remote.http.UrlHelper;
import fr.tonychouteau.weatherwidget.weather.definition.location.Coords;
import fr.tonychouteau.weatherwidget.weather.definition.Weather;
import fr.tonychouteau.weatherwidget.weather.definition.WeatherDataContainer;

public class OpenWeatherHandler {

    //=================================
    // Non-Static
    //=================================

    private final UrlHelper weatherNowUrl;
    private final UrlHelper forecastUrl;
    private final UrlHelper historyUrl;

    private final WeatherDataContainer weatherDataContainer;

    private final int dataCount;
    private static final int MAX_DATA = 10;

    //=================================
    // Constructor
    //=================================

    //https://api.openweathermap.org/data/2.5/weather?q=Lannion&appid=api_key
    //https://api.openweathermap.org/data/2.5/onecall?lat=48.73&lon=3.46&appid=api_key&exclude=minutely,daily
    //https://api.openweathermap.org/data/2.5/onecall/timemachine?lat=48.73&lon=3.46&appid=api_key&dt=today

    public OpenWeatherHandler(String apiKey, int dataCount) {
        this.dataCount = dataCount;
        String timeZone = TimeZone.getDefault().getID();
        String city = timeZone.split("/")[1].replace("_", " ");
        Log.d("9999999999","timezone="+timeZone);
        this.weatherDataContainer = new WeatherDataContainer(city, (int) Math.min(this.dataCount, MAX_DATA));

        this.weatherNowUrl = new UrlHelper(ApiHelper.API_URL + ApiHelper.WEATHER_URL)
                .param(ApiHelper.CITY, this.weatherDataContainer.getCity())
                .param(ApiHelper.API_KEY, apiKey)
                .param(ApiHelper.UNITS, ApiHelper.METRIC);

        this.forecastUrl = new UrlHelper(ApiHelper.API_URL + ApiHelper.FORECAST_URL)
                .param(ApiHelper.API_KEY, apiKey)
                .param(ApiHelper.EXCLUDE, "minutely,daily");

        this.historyUrl = new UrlHelper(ApiHelper.API_URL + ApiHelper.HISTORY_URL)
                .param(ApiHelper.API_KEY, apiKey);
    }

    //=================================
    // Public Methods
    //=================================

    public Weather makeWeather(JSONObject json) throws JSONException {
        Weather weather = new Weather();

        JSONObject weatherJson = (JSONObject) json.getJSONArray("weather").get(0);
        String iconId = weatherJson.getString("icon");

        double windSpeed;
        int windDirection;
        Date date;
        double temp = 0.0;
        if (json.has("wind")) {
            Log.d("99999999","weather="+json);
            JSONObject main = json.getJSONObject("main");
            // temp
            temp = main.getDouble("temp");
            Log.d("999999999999_","temp="+temp);
            date = new Date();
            JSONObject wind = json.getJSONObject("wind");
            windSpeed = wind.getDouble("speed");
            windDirection = wind.getInt("deg");
        } else {
            Log.d("99999999","weather2="+json);
            temp = json.getDouble("temp")-273.15;
            windSpeed = json.getDouble("wind_speed");
            windDirection = json.getInt("wind_deg");
            date = new Date(json.getInt("dt") * 1000L);
        }
        weather.updateWeather(iconId, windSpeed, windDirection, date, temp);

        return weather;
    }

    public void withWeatherData(Consumer<WeatherDataContainer> consumer, int interval) {
        AsynchJsonHandler asyncHandler = new AsynchJsonHandler();
        asyncHandler.setConsumer(json -> {
            if (json == null) return;

            Weather weather = null;
            try {
                weather = this.makeWeather(json);

                JSONObject coords = json.getJSONObject("coord");
                this.weatherDataContainer.setCoords(new Coords(
                        coords.getDouble("lat"),
                        coords.getDouble("lon")
                ));

            } catch (JSONException e) {
                e.printStackTrace();
            }

            this.weatherDataContainer.setCurrent(weather);
            this.withForecast(consumer, interval);
        });
        asyncHandler.execute(this.weatherNowUrl.make());
    }

    public void withForecast(Consumer<WeatherDataContainer> consumer, int interval) {
        AsynchJsonHandler asynchHandler = new AsynchJsonHandler();
        asynchHandler.setConsumer(json -> {
            if (json == null) return;

            ArrayList<Weather> hourlyWeather = new ArrayList<>();
            try {
                JSONArray hourlyWeatherJson = json.getJSONArray("hourly");
                for (int i = 1; i < Math.min(Math.min(MAX_DATA, dataCount) + 1, hourlyWeatherJson.length()); i++) {
                    hourlyWeather.add(this.makeWeather(hourlyWeatherJson.getJSONObject(i * interval)));
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }

            this.weatherDataContainer.setForecast(hourlyWeather);
            this.withHistory(consumer, interval);
        });
        asynchHandler.execute(
                this.forecastUrl
                        .param(ApiHelper.LAT, this.weatherDataContainer.getCoords().lat)
                        .param(ApiHelper.LON, this.weatherDataContainer.getCoords().lon)
                        .make()
        );
    }

    public void withHistory(Consumer<WeatherDataContainer> consumer, int interval) {
        AsynchJsonHandler asynchHandler = new AsynchJsonHandler();
        asynchHandler.setConsumer(json -> {
            if (json == null) return;

            ArrayList<Weather> hourlyWeather = new ArrayList<>();

            try {
                JSONArray hourlyWeatherJson = json.getJSONArray("hourly");
                for (int i = 1; i < Math.min(Math.min(MAX_DATA, dataCount), hourlyWeatherJson.length()) + 1; i++) {
                    hourlyWeather.add(this.makeWeather(hourlyWeatherJson.getJSONObject(hourlyWeatherJson.length() - i * interval)));
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }

            this.weatherDataContainer.setHistory(hourlyWeather);
            consumer.accept(this.weatherDataContainer);
        });
        asynchHandler.execute(
                this.historyUrl
                        .param(ApiHelper.LAT, this.weatherDataContainer.getCoords().lat)
                        .param(ApiHelper.LON, this.weatherDataContainer.getCoords().lon)
                        .param(ApiHelper.DATE, this.weatherDataContainer.getCurrentTimestamp())
                        .make()
        );
    }

//    public void withSkyView(Consumer<Bitmap> consumer) {
//        AsynchImageHandler asynchWeather = new AsynchImageHandler();
//        asynchWeather.setConsumer(consumer);
//        asynchWeather.execute(skyViewUrl
//                .changePath("icon", this.weather.getIcon())
//                .make());
//    }
}
