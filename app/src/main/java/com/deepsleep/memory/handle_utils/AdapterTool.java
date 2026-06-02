package com.deepsleep.memory.handle_utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdapterTool { // 玩具
    static public List<Map<String, Object>> fillInList(String[] keys, Object[]... valueArrays) {
        List<Map<String, Object>> resultList = new ArrayList<>();

        if (keys == null || keys.length == 0) {
            throw new IllegalArgumentException("Keys cannot be null or empty");
        }
        if (valueArrays.length != keys.length) {
            throw new IllegalArgumentException("Number of value arrays must match number of keys");
        }

        int arrayLength = valueArrays[0].length;

        for (int i = 0; i < arrayLength; i++) {
            Map<String, Object> itemMap = new HashMap<>();
            for (int j = 0; j < keys.length; j++) {
                if (valueArrays[j].length != arrayLength) {
                    throw new IllegalArgumentException("All value arrays must have same length");
                }
                itemMap.put(keys[j], valueArrays[j][i]);
            }
            resultList.add(itemMap);
        }
        return resultList;
    }
}