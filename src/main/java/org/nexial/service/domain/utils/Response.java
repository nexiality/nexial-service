package org.nexial.service.domain.utils;

public class Response {
    private String requestUrl;
    private String data;
    private int returnCode;
    private String statusText;
    private String detailMessage;

    public Response(String requestUrl, String data, int returnCode, String statusText, String detailMessage) {
        this.requestUrl = requestUrl;
        this.data = data;
        this.returnCode = returnCode;
        this.statusText = statusText;
        this.detailMessage = detailMessage;
    }

    public String getRequestUrl() { return requestUrl; }

    public void setRequestUrl(String requestUrl) { this.requestUrl = requestUrl; }

    public String getData() { return data; }

    public void setData(String data) { this.data = data; }

    public int getReturnCode() { return returnCode; }

    public void setReturnCode(int returnCode) { this.returnCode = returnCode; }

    public String getStatusText() { return statusText; }

    public void setStatusText(String statusText) { this.statusText = statusText; }

    public String getDetailMessage() { return detailMessage; }

    public void setDetailMessage(String errorMessage) { this.detailMessage = errorMessage; }


}

