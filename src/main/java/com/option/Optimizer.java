package com.option;

import com.option.domain.Put;
import com.option.domain.Quote;
import com.option.service.FileService;
import com.option.service.PutService;
import com.option.service.StockService;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.option.service.StockService.getEarningsDateTickers;

/**
 * https://rapidapi.com/apidojo/api/yahoo-finance1/
 */
public class Optimizer {
    private static final String optionApi = "https://apidojo-yahoo-finance-v1.p.rapidapi.com/stock/v2/get-options";
    public static final String apiKey = "88b43f2996mshb8d5ea3b3efa990p1691b9jsnbd2de64cea6d";
    public static final String apiHost = "apidojo-yahoo-finance-v1.p.rapidapi.com";
    private static final boolean afterSpecifiedDate = false; // 如果指定到期日没有数据则选择之后的日期
    private static final String expirationDate = "1631836800";
    private static final double SINGLE_TICKER_FARE_LIMITE = 15000D;
    private static final double SINGLE_TICKER_COUNT_LIMITE = 10D;
    private static final double SINGLE_TICKER_CONTRACT_COUNT_LIMITE = 5D;
    private static final List<String> exceptContracts = Arrays.asList("TSLA210917P00445000", "TSLA210917P00185000", "TSLA210917P00045000");
    private static final int rateRangeUp = 500;
    private static final int rateRangeDown = 15;


//    private static Map<String, String> tickers = Arrays.asList("tsla", "nio");
//    private static List<String> dates = Arrays.asList("1630627200", "1630627200");


    public static void main(String[] args) {

        Double amount = 36000D;
        Double buffer = 3000D;
        Double lowerRate = 0.161D;


        PutService putService = new PutService();
        StockService stockService = new StockService();
        FileService fileService = new FileService();

        List<String> tickers = fileService.readTickers();
        // first element has \ufeff
        tickers.remove(0);
        List<Put> finalPuts = new ArrayList<>();

        // 剔除 财报日 ticker
        Instant instant = Instant.now();
        long start = instant.toEpochMilli();
        Map<String, String> tickersWithEarningDateInclusive = getEarningsDateTickers(String.valueOf(start), expirationDate + "000");

        List<String> result = tickersWithEarningDateInclusive.keySet().stream()
                .distinct()
                .filter(tickers::contains)
                .collect(Collectors.toList());

        if (result.size() > 0) {
            result.forEach(
                    e -> System.out.println(tickersWithEarningDateInclusive.get(e))
            );
            tickers.removeAll(result);
            System.out.println("结果将不包含以上财报日风险ticker");
        }

// 指定ticker
//        extracted(putService, stockService, finalPuts);
        tickers.forEach(ticker -> finalPuts.addAll(processPuts(putService, stockService, ticker, expirationDate, lowerRate)));
        List<Put> no0BidPuts = finalPuts.stream().filter(put -> put.getBid() != null && put.getBid().getRaw() != 0.0).collect(Collectors.toList());
        List<Put> orderedPuts = putService.orderPuts(no0BidPuts);

        HashMap<String, Integer> mustHave = new HashMap<String, Integer>() {{
            put("tsla", 1);
        }};

        // each contract should no more than $1, should have amount up to $2
        List<Double> limitation = Arrays.asList(SINGLE_TICKER_FARE_LIMITE
                , SINGLE_TICKER_CONTRACT_COUNT_LIMITE, SINGLE_TICKER_COUNT_LIMITE);


        Map<Put, Integer> best = gimmeBest(amount, buffer, orderedPuts, mustHave, exceptContracts, limitation);

        System.out.println("======================================================================");
        System.out.printf("===============Amount: %s, Buffer: %s========================\n", amount, buffer);
        AtomicReference<Double> rest = new AtomicReference<>(0D);
        AtomicReference<Double> earn = new AtomicReference<>(0D);
        best.forEach((k, v) -> {
                    rest.updateAndGet(v1 -> v1 + k.getMargin() * v);
                    earn.updateAndGet(v1 -> v1 + k.getCurrentPrice() * v);
                    System.out.printf("卖出%s张%s, 每张保证金: %s, 总计保证金：%s，每赚一刀需要保证金：%s，当前有人出价：%s， 最新成交价：%s\n", v, k.getContractSymbol(), k.getMargin(), k.getMargin() * v, k.getRate(), k.getBid(), k.getLastPrice());
                }
        );
        System.out.printf("===============按以上操作后应剩余保证金: %s========================\n", amount - rest.get());
        System.out.printf("===============按以上操作后可以赚到: %s========================\n", earn);
    }


    private static Map<Put, Integer> gimmeBest(double amount, double buffer, List<Put> puts, HashMap<String, Integer> mustHave, List<String> exceptionContracts, List<Double> limit) {
        amount -= buffer;
        Map<Put, Integer> selectedPuts = new HashMap<>();
        Map<String, Integer> alreadyCountTicker = new HashMap<>();
        //remove exception contract symbol
        List<Put> exceptPuts = puts.stream().filter(put ->
                exceptionContracts.contains(put.getContractSymbol()) || put.getRate() < rateRangeDown || put.getRate() > rateRangeUp
        ).collect(Collectors.toList());
        puts.removeAll(exceptPuts);

        for (Put put : puts) {
            if (mustHave == null || mustHave.size() <= 0) {
                break;
            }
            if (mustHave.containsKey(put.getTicker())) {
                selectedPuts.put(put, mustHave.get(put.getTicker()));
                alreadyCountTicker.put(put.getTicker(), mustHave.get(put.getTicker()));
                amount -= put.getMargin() * mustHave.get(put.getTicker());
                if (amount <= 0) {
                    return selectedPuts;
                }
                mustHave.remove(put.getTicker());
            }
        }
        for (Put put : puts) {
            int count = 0;
            while (amount - put.getMargin() >= 0
                    && put.getMargin() * count < limit.get(0)
                    && count < limit.get(1)
                    && (alreadyCountTicker.get(put.getTicker()) == null || alreadyCountTicker.get(put.getTicker()) < limit.get(2))) {
                count++;
                amount -= put.getMargin();
            }
            if (count == 0) {
                continue;
            }
            selectedPuts.put(put, count);
            alreadyCountTicker.put(put.getTicker(), alreadyCountTicker.get(put.getTicker()) == null ? count : alreadyCountTicker.get(put.getTicker()) + count);
        }
        return selectedPuts;
    }

    private static void extracted(PutService putService, StockService stockService, List<Put> finalPuts) {
        HashMap<String, List<String>> tickers = new HashMap<String, List<String>>() {{
            // ticker: [expireDate, acceptLowerThanCurrentPriceRate]
            put("tsla", Arrays.asList("1631836800", "0.1"));
//            put("zm", Arrays.asList("1631232000", "0.2"));
//            put("BABA", Arrays.asList("1630022400", "0.1"));
//            put("aapl", Arrays.asList("1630022400", "0.1"));
//            put("fb", Arrays.asList("1630022400", "0.1"));
////            put("brk.b", Arrays.asList("1630627200", "0.1"));
//            put("nvda", Arrays.asList("1630022400", "0.1"));
//            put("jpm", Arrays.asList("1630022400", "0.1"));
        }};
        tickers.forEach((key, value) -> finalPuts.addAll(processPuts(putService, stockService, key, value.get(0), Double.parseDouble(value.get(1)))));
    }

    @SneakyThrows
    private static List<Put> processPuts(PutService putService, StockService stockService, String ticker, String date, double lowerRate) {
        Response response = getOptions(ticker, date);
        String responseJson = response.body().string();
        if (StringUtils.isBlank(responseJson)) {
            System.out.println("unable to get date for: " + ticker);
            return Collections.emptyList();
        }
        List<String> expDates = (List<String>) stockService.getExpirationDates(responseJson);
        if (!expDates.contains(date)) {
            String finalDate = date;
            /**
             * 找到指定exp日期之后的第一个可用日期 Long.valueOf(e) > Long.valueOf(finalDate)
             * 找到指定日期之前的最后一个日期 Long.valueOf(e) < Long.valueOf(finalDate) .findLast
             */
            String expiredDate;
            if (afterSpecifiedDate) {
                System.out.println("WARNING！after specified date, 可能会包含财报日风险，请自行确定财报日");
                Optional<String> toDate = expDates.stream().filter(e -> Long.parseLong(e) > Long.parseLong(finalDate)).findFirst();
                if (!toDate.isPresent()) {
                    System.out.println("no valid option date found for: " + ticker);
                    return Collections.emptyList();
                }
                expiredDate = toDate.get();
            } else {
                int index = (int) expDates.stream().filter(e -> Long.parseLong(e) < Long.parseLong(finalDate)).count() - 1;
                if (index < 0) {
                    System.out.println("没找到任何指定到期日之前的期权 for: " + ticker);
                    return Collections.emptyList();
                }
                expiredDate = expDates.get(index);
            }
            response = getOptions(ticker, expiredDate);
            responseJson = response.body().string();
        }
        Quote quote = stockService.getQuoto(responseJson);
        return putService.getOutMoneyPut(putService.getAllPutsFromJson(responseJson), lowerRate, quote, ticker);
    }

    @SneakyThrows
    public static Response getOptions(String ticker, String date) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(optionApi + "?symbol=" + ticker + "&date=" + date + "&region=US")
                .get()
                .addHeader("x-rapidapi-key", apiKey)
                .addHeader("x-rapidapi-host", apiHost)
                .build();
        return client.newCall(request).execute();
    }

}
