package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

class AppTest {

    @Test
    void applicationClass_isAnnotatedWithSpringBootApplication() {
        boolean hasAnnotation = SREmateApplication.class
                .isAnnotationPresent(SpringBootApplication.class);
        assertThat(hasAnnotation).isTrue();
    }

    @Test
    void applicationClass_hasMainMethod() throws NoSuchMethodException {
        var method = SREmateApplication.class.getDeclaredMethod("main", String[].class);
        assertThat(method).isNotNull();
        assertThat(method.getParameterCount()).isEqualTo(1);
    }
}
