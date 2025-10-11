package com.alibaba.yycome.config;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class WeatherService {
    @Tool(description = "获取指定城市的天气")
    public String getWeather(String cityName) {
        if (cityName.equals("北京")) {
            return "下雨了";
        } else {
            return "其他城市未知";
        }
    }
}
