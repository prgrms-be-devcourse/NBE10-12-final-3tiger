package com.back.pushtoken.client.dto;

/** status 가 error 면 details.error 에 "DeviceNotRegistered" 등의 코드가 담긴다. */
public record ExpoPushSendResponse(Ticket data) {

    public record Ticket(String status, String id, String message, Details details) {}

    public record Details(String error) {}
}
