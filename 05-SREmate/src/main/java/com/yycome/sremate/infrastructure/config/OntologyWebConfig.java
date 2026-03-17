package com.yycome.sremate.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 本体可视化页面固定端口配置
 * 主应用使用随机端口（server.port=0），本配置额外绑定一个固定端口，
 * 使 ontology.html 始终可通过固定地址访问。
 */
@Slf4j
@Configuration
public class OntologyWebConfig {

    @Value("${ontology.web.port:8089}")
    private int ontologyPort;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> ontologyPortCustomizer() {
        return factory -> {
            Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
            connector.setPort(ontologyPort);
            factory.addAdditionalTomcatConnectors(connector);
            log.info("[OntologyWebConfig] 本体可视化固定端口: http://localhost:{}/ontology.html", ontologyPort);
        };
    }
}
