package org.nexial.service.domain.utils;

public class ErrorHandler {
    private String requestUrl;
    private String status;
    private String message;
    private String detail;
    private String elapsedTime;

    public ErrorHandler(String requestUrl, String status, String message, String detail, String elapsedTime) {
        this.requestUrl = requestUrl;
        this.status = status;
        this.message = message;
        this.detail = detail;
        this.elapsedTime = elapsedTime;
    }
}

