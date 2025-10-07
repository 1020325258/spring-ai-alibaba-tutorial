package com.alibaba.yycome.controller;

import com.alibaba.cloud.ai.graph.StateGraph;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalysisController {



    public AnalysisController (@Qualifier("analysisGraph") StateGraph analysisGraph) {

    }

}
