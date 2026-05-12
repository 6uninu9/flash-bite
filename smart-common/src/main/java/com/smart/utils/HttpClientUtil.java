package com.smart.utils;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Http工具类
 */
@Slf4j
public class HttpClientUtil {

    // 字符集
    private static final String CHARSET = "UTF-8";

    // 请求超时时间 5秒
    static final  int TIMEOUT_MSEC = 5 * 1000;

    /**
     * 发送GET方式请求
     * @param url
     * @param paramMap
     * @return
     */
    public static String doGet(String url,Map<String,String> paramMap){
        // 创建Httpclient对象
        CloseableHttpClient httpClient = HttpClients.createDefault();

        String result = "";
        CloseableHttpResponse response = null;

        try{
            URIBuilder builder = new URIBuilder(url);
            if(paramMap != null){
                for (String key : paramMap.keySet()) {
                    builder.addParameter(key,paramMap.get(key));
                }
            }
            URI uri = builder.build();

            //创建GET请求
            HttpGet httpGet = new HttpGet(uri);

            //为GET请求设置超时配置
            httpGet.setConfig(builderRequestConfig());

            //发送请求
            response = httpClient.execute(httpGet);

            //抛出异常
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("GET请求失败，状态码：" + response.getStatusLine().getStatusCode());
            }

            return EntityUtils.toString(response.getEntity(), CHARSET);
        }catch (Exception e){ // 捕获异常
            // 记录日志
            log.error("doGet请求失败, url: {}, params: {}", url, paramMap, e);
            throw new RuntimeException("调用远程服务失败", e);
        }finally {
            // 关闭资源
            closeResources(response, httpClient);
        }
    }

    /**
     * 发送POST方式请求（表单格式）
     *
     * @param url
     * @param paramMap
     * @return
     * @throws RuntimeException 请求失败时抛出
     */
    public static String doPost(String url, Map<String, String> paramMap) {
        return doPostInternal(url, paramMap, false);
    }

    /**
     * 发送POST方式请求（JSON格式）
     *
     * @param url
     * @param paramMap
     * @return
     * @throws RuntimeException 请求失败时抛出
     */
    public static String doPost4Json(String url, Map<String, String> paramMap) {
        return doPostInternal(url, paramMap, true);
    }

    /**
     * [修改] 抽取公共的POST请求执行逻辑，减少代码重复
     *
     * @param url      请求地址
     * @param paramMap 参数
     * @param isJson   是否为JSON请求
     * @return 响应内容
     * @throws RuntimeException 请求失败时抛出
     */
    private static String doPostInternal(String url, Map<String, String> paramMap, boolean isJson) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        CloseableHttpResponse response = null;
        try {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setConfig(builderRequestConfig());

            // 设置请求体
            if (paramMap != null) {
                if (isJson) {
                    // JSON格式
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.putAll(paramMap);
                    StringEntity entity = new StringEntity(jsonObject.toString(), CHARSET);
                    entity.setContentEncoding(CHARSET);
                    entity.setContentType("application/json");
                    httpPost.setEntity(entity);
                } else {
                    // 表单格式
                    List<NameValuePair> paramList = new ArrayList<>();
                    for (Map.Entry<String, String> param : paramMap.entrySet()) {
                        paramList.add(new BasicNameValuePair(param.getKey(), param.getValue()));
                    }
                    UrlEncodedFormEntity entity = new UrlEncodedFormEntity(paramList, CHARSET);
                    httpPost.setEntity(entity);
                }
            }

            response = httpClient.execute(httpPost);

            // 检查响应状态码
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("POST请求失败，状态码：" + response.getStatusLine().getStatusCode());
            }

            return EntityUtils.toString(response.getEntity(), CHARSET);
        } catch (Exception e) {
            // 增强日志信息，包含请求类型和参数
            String requestType = isJson ? "JSON" : "表单";
            log.error("doPost{}请求失败, url: {}, params: {}", requestType, url, paramMap, e);
            throw new RuntimeException("调用远程服务失败", e);
        } finally {
            // 关闭资源
            closeResources(response, httpClient);
        }
    }

    /**
     * 统一的资源关闭，避免空指针
     *
     * @param response   HTTP响应
     * @param httpClient HTTP客户端
     */
    private static void closeResources(CloseableHttpResponse response, CloseableHttpClient httpClient) {
        try {
            if (response != null) {
                response.close();
            }
        } catch (IOException e) {
            log.error("关闭response失败", e);
        }
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (IOException e) {
            log.error("关闭httpClient失败", e);
        }
    }


    private static RequestConfig builderRequestConfig() {
        return RequestConfig.custom()
                .setConnectTimeout(TIMEOUT_MSEC)
                .setConnectionRequestTimeout(TIMEOUT_MSEC)
                .setSocketTimeout(TIMEOUT_MSEC).build();
    }

}
