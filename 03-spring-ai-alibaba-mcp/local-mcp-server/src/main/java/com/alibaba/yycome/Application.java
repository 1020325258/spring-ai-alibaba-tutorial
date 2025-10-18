package com.alibaba.yycome;

import com.alibaba.yycome.config.ScoreService;
import com.alibaba.yycome.config.WeatherService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

    @Bean
    public ToolCallbackProvider weatherTools(WeatherService weatherService) {
        return MethodToolCallbackProvider.builder().toolObjects(weatherService).build();
    }
    @Bean
    public ToolCallbackProvider scoreTools(ScoreService scoreService) {
        return MethodToolCallbackProvider.builder().toolObjects(scoreService).build();
    }
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
