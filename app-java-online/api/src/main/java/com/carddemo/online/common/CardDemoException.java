package com.carddemo.online.common;

import org.springframework.http.HttpStatus;

/**
 * Raised when a COBOL program would set WS-ERR-FLG and re-send its screen with an error message.
 * The message must be the literal text from the COBOL source.
 */
public class CardDemoException extends RuntimeException {

    private final HttpStatus status;
    private final String field;
    private final transient Object data;

    public CardDemoException(HttpStatus status, String message, String field, Object data) {
        super(message);
        this.status = status;
        this.field = field;
        this.data = data;
    }

    /** Input validation failure (HTTP 400). */
    public static CardDemoException validation(String message, String field) {
        return new CardDemoException(HttpStatus.BAD_REQUEST, message, field, null);
    }

    /** VSAM NOTFND (HTTP 404). */
    public static CardDemoException notFound(String message, String field) {
        return new CardDemoException(HttpStatus.NOT_FOUND, message, field, null);
    }

    /** Duplicate key (DUPKEY/DUPREC) or record changed by someone else (HTTP 409). */
    public static CardDemoException conflict(String message, String field) {
        return new CardDemoException(HttpStatus.CONFLICT, message, field, null);
    }

    /** Admin-only function invoked by a regular user (HTTP 403). */
    public static CardDemoException forbidden(String message) {
        return new CardDemoException(HttpStatus.FORBIDDEN, message, null, null);
    }

    /** Any other RESP code, e.g. 'Unable to lookup ...' (HTTP 500). */
    public static CardDemoException failure(String message, String field) {
        return new CardDemoException(HttpStatus.INTERNAL_SERVER_ERROR, message, field, null);
    }

    /** Same error, but still return screen data (COBOL re-sends the map with populated fields). */
    public CardDemoException withData(Object screenData) {
        return new CardDemoException(status, getMessage(), field, screenData);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getField() {
        return field;
    }

    public Object getData() {
        return data;
    }
}
