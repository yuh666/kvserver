package com.tmh.kvserver.utils;

import com.google.gson.*;


public class GsonUtils {

	private static Gson gson = null;

	static {
		gson = new GsonBuilder()
				.setDateFormat("yyyy-MM-dd HH:mm:ss")
				.create();
	}

	private GsonUtils() {
	}


	public static String toJson(Object obj) {	
		return gson.toJson(obj);
	}


	public static <T> T fromJson(String json, Class<T> clazz) {
        return (T)gson.fromJson(json, clazz);
	}
}
