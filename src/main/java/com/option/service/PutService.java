package com.option.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.option.domain.Put;
import com.option.domain.Quote;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PutService {
    private static final String optionApi = "https://apidojo-yahoo-finance-v1.p.rapidapi.com/stock/v2/get-options";



    public List<Put> getAllPutsFromJson(String jsonStr) {
        Gson gson = new Gson();
        List<Put> puts = new ArrayList<>();
        JsonObject convertedObject = gson.fromJson(jsonStr, JsonObject.class);
        JsonElement element = convertedObject.get("contracts").getAsJsonObject().get("puts");
        if (element == null) {
            System.out.println("ticker is null");
            return puts;
        }
        JsonArray array = element.getAsJsonArray();
        array.forEach(put -> puts.add(gson.fromJson(put, Put.class)));
        return puts;
    }

    public List<Put> getInMoneyPut(List<Put> puts) {
        return puts.stream().filter(Put::isInTheMoney).collect(Collectors.toList());
    }

    public List<Put> getOutMoneyPut(List<Put> puts, double lowerRate, Quote quote, String ticker) {
        return puts.stream().filter(
                put -> {
                    boolean flag =
                            !put.isInTheMoney()
                                    && put.getStrike().getRaw() < (1 - lowerRate) * quote.getCurrentPrice()
                                    && put.getStrike().getRaw() < (1 - lowerRate) * quote.getFiftyDayAverage();
                    if (flag) {
                        setPriceMarginAndRate(quote.getCurrentPrice(), put);
                        put.setTicker(ticker);
                    }
                    return flag;
                }
        ).collect(Collectors.toList());
    }

    private void setPriceMarginAndRate(double currentPrice, Put put) {
        double bidProbPrice = 0D;
        if (put.getAsk() != null && put.getBid() != null) {
            bidProbPrice = (put.getAsk().getRaw() + put.getBid().getRaw()) / 2D;
        }
        double consideratedPrice = bidProbPrice == 0D ? put.getLastPrice().getRaw() : bidProbPrice;
        double margin = calculateMargin(put, currentPrice, consideratedPrice);
        put.setMargin(margin);
        put.setCurrentPrice(currentPrice);
        put.setRate(margin / (consideratedPrice * 100));
    }

    public List<Put> orderPutByRate(List<Put> puts, double marketPrice) {
        return puts.stream().sorted(Comparator.comparingDouble(
                put -> {
                    // consider the price (bid+ask)/2 prior than lastPrice
                    setPriceMarginAndRate(marketPrice, put);
                    return put.getRate();
                }
        )).collect(Collectors.toList());
    }

    public List<Put> setPutRate(List<Put> puts, double marketPrice) {
        puts.forEach(
                put -> {
                    // consider the price (bid+ask)/2 prior than lastPrice
                    setPriceMarginAndRate(marketPrice, put);
                }
        );
        return puts;
    }

    public List<Put> orderPuts(List<Put> puts) {
        return puts.stream().sorted(Comparator.comparingDouble(
                Put::getRate
        )).collect(Collectors.toList());
    }

    public double calculateMargin(Put put, double marketPrice, double consideratedPrice) {
//        保证金=Max(权利金+（0.25 X 现时股价- 0.75 X（现时股价-行权价））X 100, 权利金 + baseMargin * strike)
        double baseMargin = 12.50D; //5 * 2.5
        final double baseMargin1 = 15.00D; //6 * 2.5
        final double baseMargin2 = 20.00D; //8 * 2.5
        double strike = put.getStrike().getRaw();
        if (strike < 25) {
            baseMargin = baseMargin2;
        } else if (strike <= 100) {
            baseMargin = baseMargin1;
        }
        return Math.max(
                consideratedPrice * 100 + 100 * (0.25 * marketPrice - 0.75 * (marketPrice - put.getStrike().getRaw())),
                baseMargin * put.getStrike().getRaw() + consideratedPrice * 100);
    }
}
