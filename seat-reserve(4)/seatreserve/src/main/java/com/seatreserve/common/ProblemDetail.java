package com.seatreserve.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * RFC 9457 "Problem Details for HTTP APIs" response body.
 * Every error response from this API conforms to this shape.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(
        String type,
        String title,
        int status,
        String detail,
        String instance
) {
    public static ProblemDetail of(String type, String title, int status, String detail, String instance) {
        return new ProblemDetail(type, title, status, detail, instance);
    }
}
