package com.option.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.option.domain.Quote;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static com.option.config.ApplicationSettings.API_HOST;
import static com.option.config.ApplicationSettings.API_KEY;

public class StockService {
    public static final String EARNINGS_API = "https://apidojo-yahoo-finance-v1.p.rapidapi.com/market/get-earnings";


    public double getCurrentPrice(Quote quote) {
        return quote.getPreMarketPrice() == 0D ? quote.getRegularMarketPrice() : quote.getPreMarketPrice();
    }

    public Quote getQuoto(String jsonStr) {
        Gson gson = new Gson();
        JsonObject convertedObject = gson.fromJson(jsonStr, JsonObject.class);
        return gson.fromJson(convertedObject.get("meta").getAsJsonObject().get("quote"), Quote.class);
    }

    public List<?> getExpirationDates(String jsonStr) {
        Gson gson = new Gson();
        JsonObject convertedObject = gson.fromJson(jsonStr, JsonObject.class);
        String[] arrName;
        try {
            arrName = gson.fromJson(convertedObject.get("meta").getAsJsonObject().get("expirationDates").getAsJsonArray(), String[].class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
        return Arrays.asList(arrName);
    }

    public static Map<String, String> getEarningsDateTickers(String start, String end) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(EARNINGS_API + "?region=US&startDate=" + start + "&endDate=" + end)
                .get()
                .addHeader("x-rapidapi-host", API_HOST)
                .addHeader("x-rapidapi-key", API_KEY)
                .build();
        try {
            Response response = client.newCall(request).execute();
            String responseJson = response.body().string();
            Gson gson = new Gson();
            JsonObject convertedObject = gson.fromJson(responseJson, JsonObject.class);
            Map<String, String> tickers = new HashMap<>();
            JsonArray array = convertedObject.getAsJsonObject().get("finance").getAsJsonObject().get("result").getAsJsonArray();
            if (array.size() >= 100) {
                System.out.println("财报数据返回数量超过API阈值，请自行确认结果中的ticker财报日日期：https://finance.yahoo.com/calendar/earnings/?symbol=ADBE");
            }
            array.forEach(
                    e -> {
                        String ticker = e.getAsJsonObject().get("ticker").getAsString();
                        long date = e.getAsJsonObject().get("startDateTime").getAsLong();
                        String type = e.getAsJsonObject().get("startDateTimeType").getAsString();
                        LocalDateTime localDateTime = LocalDateTime.ofEpochSecond(date / 1000, 0, ZoneOffset.UTC);
                        String info = ticker + "将于" + localDateTime + "(" + date + ")" + "开市前/后" + type + "发布财报";
                        tickers.put(ticker, info);
                    }
            );
            return tickers;
        } catch (Exception e) {
            System.out.printf("WARNING！unable to get earnings data from %s to %s. 请自行确认结果中的ticker财报日，注意财报日风险！", start, end);
            return new HashMap<>();
        }

    }

}
