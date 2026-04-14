package com.example.刷题.common;

import lombok.Data;

import java.io.Serializable;

@Data
public class Result<T> implements Serializable {
    private Integer code;
    private String message;
    private T data;

    private Result() {
    }

    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    public static <T> Result<T> fail() {
        return new Result<>(400, "fail", null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(400, message, null);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(Integer code, String message, T data) {
        return new Result<>(code, message, data);
    }

    public static <T> Result<T> unauthorized() {
        return new Result<>(401, "unauthorized", null);
    }

    public static <T> Result<T> forbidden() {
        return new Result<>(403, "forbidden", null);
    }

    public static <T> Result<T> notFound() {
        return new Result<>(404, "not found", null);
    }

    public static <T> Result<T> serverError() {
        return new Result<>(500, "server error", null);
    }
}