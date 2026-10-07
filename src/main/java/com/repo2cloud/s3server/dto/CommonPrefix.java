package com.repo2cloud.s3server.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class CommonPrefix {

    @JacksonXmlProperty(localName = "Prefix")
    private String prefix;

    public CommonPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}