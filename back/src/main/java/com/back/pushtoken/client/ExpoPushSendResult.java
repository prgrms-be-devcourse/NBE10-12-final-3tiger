package com.back.pushtoken.client;

/** {@code tokenExpired}(DeviceNotRegistered)면 만료된 토큰이므로 삭제 대상이다. */
public record ExpoPushSendResult(boolean succeeded, boolean tokenExpired, String message) {

    public static ExpoPushSendResult ok() {
        return new ExpoPushSendResult(true, false, null);
    }

    public static ExpoPushSendResult deviceNotRegistered() {
        return new ExpoPushSendResult(false, true, "DeviceNotRegistered");
    }

    public static ExpoPushSendResult failure(String message) {
        return new ExpoPushSendResult(false, false, message);
    }
}
