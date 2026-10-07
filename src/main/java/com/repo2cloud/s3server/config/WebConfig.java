package com.repo2cloud.s3server.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;

@Configuration
public class WebConfig {

    @Bean
    public MappingJackson2XmlHttpMessageConverter xmlConverter() {

        XmlMapper xmlMapper = new XmlMapper();

        xmlMapper.registerModule(
                new JavaTimeModule()
        );

        xmlMapper.disable(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
        );

        return new MappingJackson2XmlHttpMessageConverter(
                xmlMapper
        );
    }
}