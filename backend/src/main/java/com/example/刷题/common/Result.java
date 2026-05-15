package com.example.刷题.common;

import lombok.Data;

import java.io.Serializable;

@Data
public class Result<T> implements Serializable {
    // 统一响应类：后端所有普通接口都尽量返回这个格式，前端 request.ts 就不用为每个接口单独判断。
    // T 是泛型，表示 data 可以是用户、题目列表、验证码结果等任意业务数据。
    // 前端 request.ts 会统一识别这个结构：
    // code=200 表示成功，data 是真正的数据；其他 code 会被当成错误提示。
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
