package com.bookexpress.common.web;

public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.message = "OK";
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> fail(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.message = message;
        return r;
    }

    public boolean isSuccess() {
        return success;
    }

    public ApiResponse<T> setSuccess(boolean success) {
        this.success = success;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public ApiResponse<T> setMessage(String message) {
        this.message = message;
        return this;
    }

    public T getData() {
        return data;
    }

    public ApiResponse<T> setData(T data) {
        this.data = data;
        return this;
    }
}
