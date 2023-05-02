package com.ydyno.service.dto;

import lombok.Data;

@Data
public class ChatInfoDTO {
    private String sid;
    private String apikey;
    private String model;
    private String message;
    private String answer;
}
