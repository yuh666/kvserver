package com.tmh.kvserver.utils;

import lombok.extern.slf4j.Slf4j;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;

/**
 * @author <a href="mailto:zhouzhihui@ruubypay.com">wisdom</a>
 * Date: 2019-12-24 20:12
 * version: 1.0
 * Description:httpCilent utils
 **/
@Slf4j
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
    public static String restPost(String url, String body) {
        String result = null;
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
            if (response != null && response.getStatusLine().getStatusCode() == 200) {
                InputStream inputStream = response.getEntity().getContent();
                result = read(inputStream);
            }
        } catch (Exception e) {
            log.error("发送http请求异常,异常信息:{}", e.getMessage());
        } finally {
            if (httpPost != null) {
                httpPost.releaseConnection();
            }
            if (response != null) {
                try {
                    EntityUtils.consume(response.getEntity());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    public static String read(InputStream input) {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(input));
        String line;
        try {
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }
}