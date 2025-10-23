package com.alibaba.yycome.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchResult {
    @JsonProperty("refer")
    private String refer;
    @JsonProperty("title")
    private String title;
    @JsonProperty("link")
    private String link;
    @JsonProperty("media")
    private String media;
    @JsonProperty("content")
    private String content;
    @JsonProperty("icon")
    private String icon;
    @JsonProperty("publish_date")
    private String publish_date;
}
