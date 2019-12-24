package com.tmh.kvserver.utils;

import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;

/**
 * @author <a href="mailto:zhouzhihui@ruubypay.com">wisdom</a>
 * Date: 2019-12-24 20:12
 * version: 1.0
 * Description:httpCilent utils
 **/
public class HttpClientUtil {

    /**
     * 默认的请求媒体类型
     */
    private static final String DEFAULT_CONTENT_TYPE = "application/json;charset=UTF-8";

    /**
     * httpClient 客户端
     */
    private static volatile CloseableHttpClient httpClient = null;

    /**
     * 禁用http重试
     */
    private static DefaultHttpRequestRetryHandler requestRetryHandler = new DefaultHttpRequestRetryHandler(0,
                                                                                                           false);
    /**
     * 锁对象
     */
    private static Object                         lock                = new Object();

    /**
     * 创建httpClient对象
     *
     * @return 结果
     */
    public static CloseableHttpClient builder() {
        if (httpClient == null) {
            synchronized (lock) {
                if (httpClient == null) {
                    PoolingHttpClientConnectionManager poolManager = builderConnectionManager();
                    // 连接超时时间
                    RequestConfig defaultRequestConfig = RequestConfig.custom()
                            // 连接超时时间
                            .setConnectTimeout(5000)
                            // 读取超时时间
                            .setSocketTimeout(5000)
                            .build();

                    httpClient = HttpClientBuilder.create()
                            .setConnectionManager(poolManager)
                            .setConnectionManagerShared(true)
                            .setRetryHandler(requestRetryHandler)
                            .setDefaultRequestConfig(defaultRequestConfig)
                            .build();
                }
            }
        }
        return httpClient;
    }

    /**
     * 创建连接池
     *
     * @return 连接池
     */
    private static PoolingHttpClientConnectionManager builderConnectionManager() {
        PoolingHttpClientConnectionManager poolManager = new PoolingHttpClientConnectionManager();
        // 最大连接数
        poolManager.setMaxTotal(4000);
        // 每个路由的默认最大连接数
        poolManager.setDefaultMaxPerRoute(500);
        return poolManager;
    }

    /**
     * 转发post 请求
     *
     * @param url  请求路径
     * @param body 请求体
     */
    public static HttpResponse restPost(String url, String body) throws IOException {

        HttpPost httpPost = null;
        HttpResponse response = null;
        try {
            httpPost = new HttpPost(url);

            HashMap<String, String> headers = new HashMap<>(16);
            headers.put(org.apache.http.HttpHeaders.CONTENT_TYPE, DEFAULT_CONTENT_TYPE);
            headers.forEach(httpPost::addHeader);

            if (!StringUtils.isEmpty(body)) {
                httpPost.setEntity(new StringEntity(body, Charset.forName("UTF-8")));
            }

            response = builder().execute(httpPost);

        } finally {
            if (httpPost != null) {
                httpPost.releaseConnection();
            }
            if (response != null) {
                EntityUtils.consume(response.getEntity());
            }
        }
        return response;
    }
}