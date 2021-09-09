package com.option.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileService {

    public List<String> readTickers() {
        List<String> records = new ArrayList<>();
        try (CSVReader csvReader = new CSVReader(new FileReader("tickers.csv"));) {
            String[] values;
            while ((values = csvReader.readNext()) != null) {
                records.add(values[0]);
            }
        } catch (CsvValidationException | IOException e) {
            e.printStackTrace();
        }
        return records;
    }

}
