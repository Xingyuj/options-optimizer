package com.option.domain;

import lombok.Data;

@Data
public class Quote {
    double regularMarketOpen;
    double twoHundredDayAverage;
    double regularMarketDayHigh;
    double regularMarketPreviousClose;
    double fiftyDayAverage;
    double preMarketPrice;
    double regularMarketPrice;

    public double getCurrentPrice(){
        return this.getPreMarketPrice() == 0D ? this.getRegularMarketPrice() : this.getPreMarketPrice();
    }
}
