package com.option.domain;

import lombok.Data;

@Data
public class Put {
    String ticker;
    String contractSymbol;
    Formatter impliedVolatility;
    Formatter expiration;
    Formatter strike;
    Formatter lastPrice;
    boolean inTheMoney;
    Formatter lastTradeDate;
    Formatter ask;
    Formatter bid;
    double margin;
    double rate;
    // 标的 当前 价格
    double currentPrice;
}
