package com.example.my_bot.vk;

import com.vk.api.sdk.httpclient.HttpTransportClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

public class CustomHttpTransportClient extends HttpTransportClient {

    public CustomHttpTransportClient() {
        super();

        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(300_000) // 5 минут
                .setConnectTimeout(5_000)
                .setConnectionRequestTimeout(5_000)
                .setCookieSpec("standard")
                .build();

        PoolingHttpClientConnectionManager connectionManager =
                new PoolingHttpClientConnectionManager();

        connectionManager.setMaxTotal(300);
        connectionManager.setDefaultMaxPerRoute(300);

        httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setUserAgent("Java VK SDK/1.0")
                .build();
    }
}