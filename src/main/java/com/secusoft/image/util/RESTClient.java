package com.secusoft.image.util;

import org.apache.http.*;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLHandshakeException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Http请求客户端
 * @author ChenYongHeng
 * @since 2019/5/27
 */
public class RESTClient {


    private static Logger log = LoggerFactory.getLogger(RESTClient.class);



    private volatile static RESTClient HttpClientConnectionPool;

    private static final String USERAGENT = "SZ-JAVA";
    private static final String CHARSET = "UTF-8";
    private static final int MAX_TOTAL_CONNECTIONS = 200;
    private static final int MAX_ROUTE_CONNECTIONS = 100;
    /**
     * 连接时间
     */
    private static final int CONNECT_TIMEOUT = 5000;
    /**
     * 获取内容时间
     */
    private static final int SOCKET_TIMEOUT = 30000;

    private static PoolingHttpClientConnectionManager cm = null;
    private static CloseableHttpClient httpclient;



    /**
     * 初始化连接池
     */
    static{
        try {
            cm = new PoolingHttpClientConnectionManager();
            cm.setMaxTotal(MAX_TOTAL_CONNECTIONS);
            // 默认设置为2
            cm.setDefaultMaxPerRoute(MAX_ROUTE_CONNECTIONS);

            // 客户端请求的默认设置
            RequestConfig defaultRequestConfig = RequestConfig.custom()
                    .setSocketTimeout(SOCKET_TIMEOUT)
                    .setConnectTimeout(CONNECT_TIMEOUT)
                    .setConnectionRequestTimeout(CONNECT_TIMEOUT)
                    .setRedirectsEnabled(false)
                    .setCookieSpec(CookieSpecs.STANDARD_STRICT)
                    .build();

            // 请求重试处理
            HttpRequestRetryHandler httpRequestRetryHandler = new HttpRequestRetryHandler() {
                @Override
                public boolean retryRequest(IOException exception,
                                            int executionCount, HttpContext context) {
                    // 如果超过最大重试次数，那么就不要继续了
                    if (executionCount >= 2) {
                        return false;
                    }

                    // 如果服务器丢掉了连接，那么就重试
                    if (exception instanceof NoHttpResponseException) {
                        return true;
                    }
                    // 不要重试SSL握手异常
                    if (exception instanceof SSLHandshakeException) {
                        return false;
                    }
                    HttpRequest request = (HttpRequest) context.getAttribute(HttpClientContext.HTTP_REQUEST);
                    boolean idempotent = !(request instanceof HttpEntityEnclosingRequest);
                    // 如果请求被认为是幂等的，那么就重试
                    if (idempotent) {
                        return true;
                    }

                    return false;
                }

            };

            httpclient = HttpClients.custom()
                    .setConnectionManager(cm)
                    .setDefaultRequestConfig(defaultRequestConfig)
                    .setRetryHandler(httpRequestRetryHandler)
                    .build();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private RESTClient(){}

    /**
     * 获取HttpClientConnectionPool对象，这是单例方法
     *
     * @return
     */
    public static RESTClient getClientConnectionPool() {
        if (HttpClientConnectionPool == null) {
            synchronized (RESTClient.class) {
                if (HttpClientConnectionPool == null) {
                    HttpClientConnectionPool = new RESTClient();
                }
            }
        }
        return HttpClientConnectionPool;
    }

    /**
     * 获取HttpClient。在获取之前，确保HttpClientConnectionPool对象已创建。
     * @return
     */
    public static CloseableHttpClient getHttpClient() {
        return httpclient;
    }


    /**
     * 关闭整个连接池
     */
    public static void close() {
        if (cm != null) {
            cm.shutdown();
        }
        if(httpclient != null){
            try {
                httpclient.close();
            } catch (IOException e) {
                log.error(e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Get方法封装，发get送请求，获取响应内容
     */
    public String fetchByGetMethod(String getUrl){
        String charset = null;
        HttpGet httpget = null;
        String responseStr = null;
        String seqNum = String.valueOf(System.currentTimeMillis());
        try {
            httpget = new HttpGet(getUrl);

            if(!getUrl.toLowerCase().contains("m3u8")){
                log.info(seqNum+">>> "+httpget.toString());
            }

            HttpResponse response = null;

            response = httpclient.execute(httpget);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK) {
                if(!getUrl.toLowerCase().contains("m3u8")){
                    log.error("statusCode=" + statusCode);
                    log.error(getUrl + "HttpGet Method failed: " + response.getStatusLine());
                }
                return null;
            }

            HttpEntity entity = response.getEntity();
            if(entity == null){
                return null;
            }

            byte[] bytes = getByteArrayOutputStream(entity.getContent());
            if(bytes == null){
                return null;
            }

            // 从content-type中获取编码方式
            Header header=response.getFirstHeader("Content-Type");
            if(header != null) {
                charset = getCharSetFromContentType(header.getValue());
            }
            if(charset != null && !"".equals(charset)){
                charset = charset.replace("\"", "");
                if("gb2312".equalsIgnoreCase(charset)){
                    charset = "GBK";
                }
                responseStr = new String(bytes, Charset.forName(charset));
            }else{//从Meta中获取编码方式
                responseStr = new String(bytes,Charset.forName("utf-8"));
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            log.error(getUrl + "抓取失败");
            e.printStackTrace();
        } finally{
            httpget.abort();
        }

        if(!getUrl.toLowerCase().contains("m3u8")){
            log.info(seqNum+"<<< "+responseStr);
        }

        return responseStr;
    }


    /**
     * Post方法封装，发送post请求，获取响应内容
     */
    public String fetchByPostMethod(String url, String jsonStr){
        String resultStr = null;
        HttpPost httpPost = new HttpPost(url);
        httpPost.setEntity(new StringEntity(jsonStr, ContentType.APPLICATION_JSON));
        httpPost.addHeader(HttpHeaders.USER_AGENT,USERAGENT);
		httpPost.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());

        String seqNum = String.valueOf(System.currentTimeMillis());
        log.info(seqNum+">>> "+httpPost.toString());
        log.info(seqNum+"Body>>> " + jsonStr);
        HttpResponse response;
        try {
            response = httpclient.execute(httpPost);
            HttpEntity entity = response.getEntity();
            resultStr = EntityUtils.toString(entity,CHARSET);
            EntityUtils.consume(entity);
        }catch (ConnectException ce){// 服务器请求失败
            log.error(ce.getMessage());
        } catch (IOException e) {
            log.error(e.getMessage());
        } finally{
            httpPost.abort();
        }
        log.info(seqNum+"<<< "+resultStr);
        return resultStr;
    }

    public static byte[] getByteArrayOutputStream(InputStream is) {
        ByteArrayOutputStream bios = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try {
            int len = -1;
            while ((len = is.read(buffer)) != -1) {
                bios.write(buffer, 0, len);
                buffer = new byte[4096];
            }
            return bios.toByteArray();
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        } finally {
            try {
                if(is != null){
                    is.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            if (bios != null) {
                try {
                    bios.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 正则获取字符编码
     * @param content_type
     * @return
     */
    private static String getCharSetFromContentType(String content_type){
        String regex = "charset=\\s*(\\S*[^;])";
        Pattern pattern = Pattern.compile(regex,Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content_type);
        if(matcher.find()){
            return matcher.group(1);

        }else{
            return null;
        }
    }



}
