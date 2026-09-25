package com.scriptmanager.http;

/** 业务异常：携带 HTTP 状态码，由接口层统一转换为错误响应。 */
public class ApiError extends RuntimeException {

    public final int status;

    public ApiError(int status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiError notFound(String message) {
        return new ApiError(404, message);
    }

    public static ApiError badRequest(String message) {
        return new ApiError(400, message);
    }
}
