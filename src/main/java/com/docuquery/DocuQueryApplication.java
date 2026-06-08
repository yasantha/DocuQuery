package com.docuquery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DocuQueryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocuQueryApplication.class, args);
    }
}
