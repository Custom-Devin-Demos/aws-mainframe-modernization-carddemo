package com.carddemo.online.common;

/**
 * Envelope returned by every endpoint; the REST equivalent of a BMS map send.
 *
 * @param data        screen payload (output fields of the map)
 * @param message     text of the WS-MESSAGE / ERRMSG line, verbatim from the COBOL source
 * @param messageType how the message line should be rendered
 * @param field       input field the COBOL program positioned the cursor on ({@code MOVE -1 TO xxxL})
 * @param nextProgram program the CICS code would XCTL to (e.g. {@code COADM01C}); the UI routes to its page
 */
public record ScreenResponse<T>(T data, String message, MessageType messageType, String field, String nextProgram) {

    public static <T> ScreenResponse<T> of(T data) {
        return new ScreenResponse<>(data, null, null, null, null);
    }

    public static <T> ScreenResponse<T> of(T data, String message) {
        return new ScreenResponse<>(data, message, MessageType.INFO, null, null);
    }

    public static <T> ScreenResponse<T> success(T data, String message) {
        return new ScreenResponse<>(data, message, MessageType.SUCCESS, null, null);
    }

    public static <T> ScreenResponse<T> error(String message, String field) {
        return new ScreenResponse<>(null, message, MessageType.ERROR, field, null);
    }

    public ScreenResponse<T> withField(String newField) {
        return new ScreenResponse<>(data, message, messageType, newField, nextProgram);
    }

    public ScreenResponse<T> withNextProgram(String program) {
        return new ScreenResponse<>(data, message, messageType, field, program);
    }
}
