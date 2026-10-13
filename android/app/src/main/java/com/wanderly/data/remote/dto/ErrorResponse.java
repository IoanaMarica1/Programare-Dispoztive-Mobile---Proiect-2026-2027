package com.wanderly.data.remote.dto;

import java.util.Map;
public class ErrorResponse {
    public int status;
    public String message;
    public Map<String, String> errors;
}