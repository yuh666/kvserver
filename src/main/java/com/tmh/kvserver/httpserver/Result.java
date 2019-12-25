package com.tmh.kvserver.httpserver;


public class Result<T> {


    private int    code;
    private String msg;
    private T      body;

    private Result(int code, String msg, T body) {
        this.code = code;
        this.msg = msg;
        this.body = body;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getBody() {
        return body;
    }

    public void setBody(T body) {
        this.body = body;
    }

    public static <T> Result<T> success(T body) {
        return new Result<T>(0, "ok", body);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<T>(code, msg, null);
    }

}