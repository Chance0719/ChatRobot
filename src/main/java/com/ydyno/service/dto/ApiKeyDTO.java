package com.ydyno.service.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ApiKeyDTO implements Serializable {
    private String sid;
    private String apikey;
}
