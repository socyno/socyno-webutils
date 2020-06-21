package com.socyno.webbsc.ctxsrv;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.socyno.base.bscexec.MessageException;
import com.socyno.base.bscmixutil.CommonUtil;
import com.socyno.base.bscmixutil.JsonUtil;
import com.socyno.base.bscmixutil.StringUtils;
import com.socyno.base.bscmodel.R;
import com.socyno.base.bscservice.HttpUtil;
import com.socyno.webbsc.ctxutil.ContextHttpClient;
import com.socyno.webbsc.ctxutil.HttpMessageConverter;
@Slf4j
@Getter
@Setter
public class AbstractInternalRestService {
    
    public static enum Method {
        GET, POST, PUT, DELTE;
    }
    
    public static class QueryMapData {
        private final Map<String, Object> data
                    = new HashMap<String, Object>();
        
        public QueryMapData clear() {
            data.clear();
            return this;
        }
        
        public QueryMapData put(String key, Object value) {
            data.put(key, value);
            return this;
        }
        
        public QueryMapData putAll(Map<String, Object> pairs) {
            if (pairs != null) {
                data.putAll(pairs);
            }
            return this;
        }

        public QueryMapData remove(String key) {
            data.remove(key);
            return this;
        }
        
        public Map<String, Object> asMap() {
            return data;
        }
    }
    
    private int timeoutMS;
    private String charset;
    private String dateformat = "yyyy-MM-dd HH:mm:ss";
    private final String url;

    private final static int HTTP_TIMEOUT_DEFAULT = 20000;
    private final static String HTTP_CHARSET_DEFAULT = "UTF-8";
    
    protected HttpUtil getHttpClient() {
    	return ContextHttpClient.getDefault();
    }
    
    /**
     * 构造器
     * @param url       请求URL根路径
     * @param timeoutMS 请求超时（默认 20s，指连接超时或请求响应超时限制）
     * @param charset   编码方式（默认 UTF-8）
     * @throws URISyntaxException
     */
    public AbstractInternalRestService(String url) throws URISyntaxException {
        this(url, HTTP_TIMEOUT_DEFAULT, HTTP_CHARSET_DEFAULT);
    }
    
    /**
     * 构造器
     * @param url       请求URL根路径
     * @param timeoutMS 请求超时（默认 20s，指连接超时或请求响应超时限制）
     * @param charset   编码方式（默认 UTF-8）
     * @throws URISyntaxException
     */
    public AbstractInternalRestService(String url, String charset) throws URISyntaxException {
        this(url, HTTP_TIMEOUT_DEFAULT, charset);
    }
    
    /**
     * 构造器
     * @param url       请求URL根路径
     * @param timeoutMS 请求超时（默认 20s，指连接超时或请求响应超时限制）
     * @param charset   编码方式（默认 UTF-8）
     * @throws URISyntaxException
     */
    public AbstractInternalRestService(String url, int timeoutMS) throws URISyntaxException {
        this(url, timeoutMS, HTTP_CHARSET_DEFAULT);
    }
    
    /**
     * 构造器
     * @param url       请求URL根路径
     * @param timeoutMS 请求超时（默认 20s，指连接超时或请求响应超时限制）
     * @param charset   编码方式（默认 UTF-8）
     * @throws URISyntaxException
     */
    public AbstractInternalRestService(String url, int timeoutMS, String charset)
            throws URISyntaxException  {
        this.charset = charset;
        this.timeoutMS = timeoutMS;
        URI uri = new URI(url);
        StringBuffer burl = new StringBuffer()
                    .append(uri.getScheme())
                    .append("://")
                    .append(uri.getHost());
        int port;
        if ((port = uri.getPort()) > 0) {
            burl.append(":").append(port);
        }
        String path = pathFormatter(uri.getPath(), true);
        this.url = burl.append(path).toString();
    }
    
    public String getCharset() {
        return StringUtils.ifBlank(charset, HTTP_CHARSET_DEFAULT);
    }
    
    /**
     * 请求路径规范化函数
     *  1. 在路径前添加 /（如不存在）
     *  2. 移除路径尾部的 /（trimEnds=true）
     */
    private String pathFormatter(String path, boolean trimEnds) {
        if (trimEnds) {
            while ((path = StringUtils.trimToEmpty(path)).endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }
    
    /**
     * 获取一个空的 QueryMapData 对象
     */
    public static QueryMapData newQueryMapData() {
        return new QueryMapData();
    }
    
    /**
     * 将给定的 mapdata 转换成 form-urlencoded 形式的字节码数组。
     * @param mapdata QueryMapData 对象，如为 NULL 则返回也为 NULL 值。
     * @throws UnsupportedEncodingException
     */
    private byte[] toQueryBytes(QueryMapData mapdata)
                        throws UnsupportedEncodingException {
        if (mapdata == null || mapdata.data == null) {
            return null;
        }
        return HttpUtil.toQueryString(mapdata.data)
                            .getBytes(getCharset());
    }
    
    /**
     * POST请求。
     * 
     * @param clazz       数据类型
     * @param path        请求地址
     * @return            指定数据类型的对象。注意：如果对象是集合或数组，请使用 list 方法。
     * @throws IOException
     */
    public <T> T post(Class<T> clazz, String path)
            throws IOException {
        return post(clazz, path, (Object)null, (Map<String, Object>)null);
    }
    
    /**
     * POST请求。
     * 
     * @param clazz       数据类型
     * @param path        请求地址
     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
     * @return            指定数据类型的对象。注意：如果对象是集合或数组，请使用 list 方法。
     * @throws IOException
     */
    public <T> T post(Class<T> clazz, String path, Object data)
            throws IOException {
        return post(clazz, path, data, (Map<String, Object>)null);
    }
    
    /**
     * POST请求。
     * 
     * @param clazz       数据类型
     * @param path        请求地址
     * @param query       请求URL参数
     * @return            指定数据类型的对象。注意：如果对象是集合或数组，请使用 list 方法。
     * @throws IOException
     */
    public <T> T post(Class<T> clazz, String path, Map<String, Object> query)
            throws IOException {
        return post(clazz, path, (Object)null, query);
    }
    
    /**
     * POST 请求.
     * 
     * @param clazz 响应对象类型
     * @param path  请求地址
     * @param query 请求BODY数据（form-urlencoded形式）
     */
    public <T> T post(Class<T> clazz, String path, QueryMapData query)
            throws IOException {
        return post(clazz, path, (Object)query, (Map<String, Object>)null);
    }
    
    /**
     * POST 请求.
     * 
     * @param clazz 响应对象类型。若为 R 类型，则返回整个请求内容。
     * @param path 请求地址（相对URL）
     * @param data
     * 请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
     * @param query 请求URL参数（Query String）
     */
    public <T> T post(Class<T> clazz, String path, Object data, Map<String, Object> query) throws IOException {
        return post(clazz, path, data, query, null);
    }
    
    /**
     * POST 请求.
     * 
     * @param clazz 响应对象类型。若为 R 类型，则返回整个请求内容。
     * @param path 请求地址（相对URL）
     * @param data
     * 请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
     * @param query 请求URL参数（Query String）
     * @param headers 请求头
     * @return
     * @throws IOException
     */
    public <T> T post(Class<T> clazz, String path, Object data, Map<String, Object> query, Map<String, Object> headers)
            throws IOException {
        return post(clazz, path, data, query, headers, true);
    }
    
    /**
     * 
     * @param clazz 响应对象类型。若为 R 类型，则返回整个请求内容。
     * @param path 请求地址（相对URL）
     * @param data
     * 请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
     * @param query 请求URL参数（Query String）
     * @param headers 请求头
     * @param isCheckStatus 是否检查返回状态码, 当请求的响应码非 0 时，视为请求失败。
     * @return
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    public <T> T post(Class<T> clazz, String path, Object data, Map<String, Object> query, Map<String, Object> headers,
            boolean isCheckStatus) throws IOException {
        R r = request(Method.POST, path, query, data, headers, isCheckStatus);
        if (clazz == null) {
            return null;
        }
        if (R.class.equals(clazz)) {
            return (T) r;
        }
        return fromObject(r.getData(), clazz);
    }
    
    @SuppressWarnings("unused")
    private R request(Method method, String pathQuery, Map<String, Object> query, Object data) throws IOException {
        return request(method, pathQuery, query, data, null);
    }
    
    private R request(Method method, String pathQuery, Map<String, Object> query, Object data,
            Map<String, Object> headers) throws IOException {
        return request(method, pathQuery, query, data, headers, true);
    }
    
    private R request(Method method, String pathQuery, Map<String, Object> query, Object data,
            Map<String, Object> headers, boolean isCheckStatus) throws IOException {
        byte[] body = null;
        if (headers == null) {
            headers = new HashMap<String, Object>();
        }
        headers.put("Content-Type", String.format("application/x-www-form-urlencoded;charset=%s", getCharset()));
        if (data instanceof QueryMapData) {
            body = toQueryBytes((QueryMapData) data);
        } else if (data != null) {
            body = JsonUtil.toJson(data).getBytes(getCharset());
            headers.put("Content-Type", String.format("application/json;charset=%s", getCharset()));
        }
		String fullUrl = StringUtils.isBlank(pathQuery) ? url
				: HttpUtil.concatUrlPath(url, pathFormatter(pathQuery, false));
		CloseableHttpResponse resp = null;
		try {
			resp = getHttpClient().request(fullUrl, CommonUtil.ifNull(method, Method.GET).name(), query, headers, body,
					timeoutMS);
			String bodyText = HttpUtil.getResponseText(resp, getCharset());
			R rx = transform(bodyText, resp);
			if (rx == null) {
				log.error("Response body : {}", bodyText);
				throw new MessageException("No expected response data.");
			}
			if (isCheckStatus) {
				if (rx.getStatus() != 0) {
					throw new MessageException(rx.getMessage());
				}
			}
			return rx;
		} finally {
			HttpUtil.close(resp);
		}
    }
//    /**
//     * RESTFUL CREATE 方法：同POST请求。
//     * 
//     * @param path        请求地址
//     * @param query       请求URL参数
//     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
//     * @throws IOException
//     */
//    public void create(String path) throws IOException {
//        create(null, null, path, null);
//    }
//    
//    /**
//     * RESTFUL CREATE 方法：同POST请求。
//     * 
//     * @param path        请求地址
//     * @param query       请求URL参数
//     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
//     * @throws IOException
//     */
//    public void create(String path, Object data) throws IOException {
//        create(null, path, data, null);
//    }
//    
//    /**
//     * RESTFUL CREATE 方法：同POST请求。
//     * 
//     * @param path        请求地址
//     * @param query       请求URL参数
//     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
//     * @throws IOException
//     */
//    public void create(String path, Object data, Map<String, Object> query) throws IOException {
//        create(null, path, data, query);
//    }
//    
//    /**
//     * RESTFUL CREATE 方法：同POST请求。
//     * 
//     * @param clazz       数据类型
//     * @param path        请求地址
//     * @param query       请求URL参数
//     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
//     * @return            指定数据类型的对象。注意：如果对象是集合或数组，请使用 list 方法。
//     * @throws IOException
//     */
//    public <T> T create(Class<T> clazz, String path) throws IOException {
//        return create(clazz, path, null, null);
//    }
//    
//    /**
//     * RESTFUL CREATE 方法：同POST请求。
//     * 
//     * @param clazz       数据类型
//     * @param path        请求地址
//     * @param query       请求URL参数
//     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
//     * @return            指定数据类型的对象。注意：如果对象是集合或数组，请使用 list 方法。
//     * @throws IOException
//     */
//    public <T> T create(Class<T> clazz, String path, Object data) throws IOException {
//        return create(clazz, path, data, null);
//    }
//    
//    /**
//     * RESTFUL CREATE 方法：同POST请求。
//     * 
//     * @param clazz       数据类型
//     * @param path        请求地址
//     * @param query       请求URL参数
//     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
//     * @return            指定数据类型的对象。注意：如果对象是集合或数组，请使用 list 方法。
//     * @throws IOException
//     */
//	public <T> T create(Class<T> clazz, String path, Object data, Map<String, Object> query)
//            throws IOException {
//        return post(clazz, path, data, query);
//    }
//	
//    /**
//     * RESTFUL DELETE 方法：线上禁止 DELETE 方式，使用POST替代。
//     * 
//     * @param path        请求地址
//     * @throws IOException
//     */
//    public void delete(String path) throws IOException {
//        delete(path, null, null);
//    }
//    
//    /**
//     * RESTFUL DELETE 方法：线上禁止 DELETE 方式，使用POST替代。
//     * 
//     * @param path        请求地址
//     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
//     * @throws IOException
//     */
//    public void delete(String path, Object data) throws IOException {
//        delete(null, path, null, data);
//    }
//    
//    /**
//     * RESTFUL DELETE 方法：线上禁止 DELETE 方式，使用POST替代。
//     * 
//     * @param path        请求地址
//     * @param query       请求URL参数
//     * @throws IOException
//     */
//    public void delete(String path, Map<String, Object> query) throws IOException {
//        delete(null, path, query, null);
//    }
//    
//    /**
//     * RESTFUL DELETE 方法：线上禁止 DELETE 方式，使用POST替代。
//     * 
//     * @param path        请求地址
//     * @param query       请求URL参数
//     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
//     * @throws IOException
//     */
//    public void delete(String path, Map<String, Object> query, Object data)
//            throws IOException {
//        delete(null, path, query, data);
//    }
//    
//    /**
//     * RESTFUL DELETE 方法：线上禁止 DELETE 方式，使用POST替代。
//     * 
//     * @param clazz       数据类型
//     * @param path        请求地址
//     * @param query       请求URL参数
//     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
//     * @return            指定数据类型的对象。注意：如果对象是集合或数组，请使用 list 方法。
//     * @throws IOException
//     */
//    public <T> T delete(Class<T> clazz, String path) throws IOException {
//        return delete(clazz, path, null, null);
//    }
//    
//    /**
//     * RESTFUL DELETE 方法：线上禁止 DELETE 方式，使用POST替代。
//     * 
//     * @param clazz       数据类型
//     * @param path        请求地址
//     * @param query       请求URL参数
//     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
//     * @return            指定数据类型的对象。注意：如果对象是集合或数组，请使用 list 方法。
//     * @throws IOException
//     */
//    public <T> T delete(Class<T> clazz, String path, Map<String, Object> query) throws IOException {
//        return delete(clazz, path, query, null);
//    }
//    
//    /**
//     * RESTFUL DELETE 方法：线上禁止 DELETE 方式，使用POST替代。
//     * 
//     * @param clazz       数据类型
//     * @param path        请求地址
//     * @param query       请求URL参数
//     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
//     * @return            指定数据类型的对象。注意：如果对象是集合或数组，请使用 list 方法。
//     * @throws IOException
//     */
//    public <T> T delete(Class<T> clazz, String path, Object object) throws IOException {
//        return delete(clazz, path, null, object);
//    }
//    
//    /**
//     * RESTFUL DELETE 方法：线上禁止 DELETE 方式，使用POST替代。
//     * 
//     * @param clazz       数据类型
//     * @param path        请求地址
//     * @param query       请求URL参数
//     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
//     * @return            指定数据类型的对象。注意：如果对象是集合或数组，请使用 list 方法。
//     * @throws IOException
//     */
//	public <T> T delete(Class<T> clazz, String path, Map<String, Object> query, Object data)
//            throws IOException {
//        return post(clazz, path, data, query);
//    }
//    
    /**
     * RESTFUL GET 方法 ： GET请求。
     * 
     * @param clazz       数据类型
     * @param path        请求地址
     * @return            指定数据类型的对象。注意：如果对象是集合或数组，请使用 list 方法。
     * @throws IOException
     */
    public <T> T get(@NonNull Class<T> clazz, String path) throws IOException {
        return get(clazz, path, null);
    }
    
    /**
     * RESTFUL GET 方法 ： GET请求。
     * 
     * @param clazz       数据类型
     * @param path        请求地址
     * @return            指定数据类型的对象。注意：如果对象是集合或数组，请使用 list 方法。
     * @throws IOException
     */
    public <T> T get(@NonNull Class<T> clazz, String path, Map<String, Object> query) throws IOException {
        return get(clazz, path, null, null);
    }

    public <T> T get(Class<T> clazz, String path, Map<String, Object> query, Map<String, Object> headers)
            throws IOException {
        return get(clazz, path, query, headers,true);
    }
    /**
     * RESTFUL GET 方法 ： GET请求。
     * 
     * @param clazz       数据类型
     * @param path        请求地址
     * @param query       请求URL参数
     * @return            指定数据类型的对象。注意：如果对象是集合或数组，请使用 list 方法。
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> clazz, String path, Map<String, Object> query, Map<String, Object> headers,boolean isCheckStatus)
            throws IOException {
        R r = request(Method.GET, path, query, null, headers,isCheckStatus);
        if (clazz == null) {
            return null;
        }
        if (R.class.equals(clazz)) {
            return (T) r;
        }
        return fromObject(r.getData(), clazz);
    }
    
//    /**
//     * 当返回数据为列表或数组时，必须使用此方法进行数据反序列化（默认 GET 请求）。
//     * 
//     * @param clazz       列表内的数据类型
//     * @param path        请求地址（默认请求 GET）
//     * @return            数据列表
//     * @throws IOException
//     */
//    public <T> List<T> list(@NonNull Class<T> clazz, String path) throws IOException {
//        return list(clazz, path, (Map<String, Object>)null, null);
//    }
//    
//    /**
//     * 当返回数据为列表或数组时，必须使用此方法进行数据反序列化（默认 GET 请求）。
//     * 
//     * @param clazz       列表内的数据类型
//     * @param path        请求地址（默认请求 GET）
//     * @param query       请求URL参数
//     * @return            数据列表
//     * @throws IOException
//     */
//    public <T> List<T> list(@NonNull Class<T> clazz, String path, Map<String, Object> query) throws IOException {
//        return list(clazz, path, query, null);
//    }
//    
//    /**
//     * 当返回数据为列表或数组时，必须使用此方法进行数据反序列化。
//     * 
//     * @param clazz       列表内的数据类型
//     * @param path        请求地址
//     * @param method      请求方式
//     * @return            数据列表
//     * @throws IOException
//     */
//    public <T> List<T> list(Class<T> clazz, String path, Method method)
//                throws IOException {
//        return list(clazz, path, (Map<String, Object>)null, method);
//    }
//    
//    /**
//     * 当返回数据为列表或数组时，必须使用此方法进行数据反序列化。
//     * 
//     * @param clazz       列表内的数据类型
//     * @param path        请求地址
//     * @param query       请求URL参数
//     * @param method      请求方式（默认 GET）
//     * @return            数据列表
//     * @throws IOException
//     */
//    public <T> List<T> list(Class<T> clazz, String path, Map<String, Object> query, Method method)
//                throws IOException {
//        return list(clazz, path, query, method, null);
//    }
//    
//    /**
//     * 当返回数据为列表或数组时，必须使用此方法进行数据反序列化。
//     * 
//     * @param clazz       列表内的数据类型
//     * @param path        请求地址
//     * @param query       请求BODY参数（将以form-urlencoded形式添加到BODY中。）
//     * @param method      请求方式（默认 GET）
//     * @return            数据列表
//     * @throws IOException
//     */
//    public <T> List<T> list(Class<T> clazz, String path, QueryMapData query, Method method)
//                throws IOException {
//        return list(clazz, path, null, method, query);
//    }
//    
//    /**
//     * 当返回数据为列表或数组时，必须使用此方法进行数据反序列化。
//     * 
//     * @param clazz       列表内的数据类型
//     * @param path        请求地址
//     * @param method      请求方式
//     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
//     * @return            数据列表
//     * @throws IOException
//     */
//    public <T> List<T> list(Class<T> clazz, String path, Method method, Object data)
//                throws IOException {
//        return list(clazz, path, null, method, data);
//    }
//    
//    /**
//     * 当返回数据为列表或数组时，必须使用此方法进行数据反序列化。
//     * 
//     * @param clazz       列表内的数据类型
//     * @param path        请求地址
//     * @param query       请求URL参数
//     * @param method      请求方式（默认 GET）
//     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
//     * @return            数据列表
//     * @throws IOException
//     */
//    public <T> List<T> list(Class<T> clazz, String path, Map<String, Object> query, Method method, Object data)
//                throws IOException {
//        R r = request(method, path, query, data);
//        if (clazz == null) {
//            return null;
//        }
//        List<T> list = new ArrayList<T>();
//        for (Object o : (List<?>) r.getData()) {
//            list.add(fromObject(o, clazz));
//        }
//        return list; 
//    }
    
//    /**
//     * 当返回数据为Map<String, ?>对象时，必须使用此方法进行数据反序列化（默认 GET 请求）。
//     * 
//     * @param clazz       对象内的数据类型
//     * @param path        请求地址（默认请求 GET）
//     * @return            数据对象
//     * @throws IOException
//     */
//    public <T> Map<String, T> map(@NonNull Class<T> clazz, String path) throws IOException {
//        return map(clazz, path, (Map<String, Object>)null, null);
//    }
//    
//    /**
//     * 当返回数据为Map<String, ?>对象时，必须使用此方法进行数据反序列化（默认 GET 请求）。
//     * 
//     * @param clazz       对象内的数据类型
//     * @param path        请求地址（默认请求 GET）
//     * @param query       请求URL参数
//     * @return            数据对象
//     * @throws IOException
//     */
//    public <T> Map<String, T> map(@NonNull Class<T> clazz, String path, Map<String, Object> query) throws IOException {
//        return map(clazz, path, query, null);
//    }
//    
//    /**
//     * 当返回数据为Map<String, ?>对象时，必须使用此方法进行数据反序列化。
//     * 
//     * @param clazz       对象内的数据类型
//     * @param path        请求地址
//     * @param method      请求方式
//     * @return            数据对象
//     * @throws IOException
//     */
//    public <T> Map<String, T> map(Class<T> clazz, String path, Method method)
//                throws IOException {
//        return map(clazz, path, (Map<String, Object>)null, method);
//    }
//    
//    /**
//     * 当返回数据为Map<String, ?>对象时，必须使用此方法进行数据反序列化。
//     * 
//     * @param clazz       对象内的数据类型
//     * @param path        请求地址
//     * @param query       请求URL参数
//     * @param method      请求方式（默认 GET）
//     * @return            数据对象
//     * @throws IOException
//     */
//    public <T> Map<String, T> map(Class<T> clazz, String path, Map<String, Object> query, Method method)
//                throws IOException {
//        return map(clazz, path, query, method, null);
//    }
//    
//    /**
//     * 当返回数据为Map<String, ?>对象时，必须使用此方法进行数据反序列化。
//     * 
//     * @param clazz       对象内的数据类型
//     * @param path        请求地址
//     * @param query       请求BODY参数（将以form-urlencoded形式添加到BODY中。）
//     * @param method      请求方式（默认 GET）
//     * @return            数据对象
//     * @throws IOException
//     */
//    public <T> Map<String, T> map(Class<T> clazz, String path, QueryMapData query, Method method)
//                throws IOException {
//        return map(clazz, path, null, method, query);
//    }
//    
//    /**
//     * 当返回数据为Map<String, ?>对象时，必须使用此方法进行数据反序列化。
//     * 
//     * @param clazz       对象内的数据类型
//     * @param path        请求地址
//     * @param method      请求方式
//     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
//     * @return            数据对象
//     * @throws IOException
//     */
//    public <T> Map<String, T> map(Class<T> clazz, String path, Method method, Object data)
//                throws IOException {
//        return map(clazz, path, null, method, data);
//    }
//    
//    /**
//     * 当返回数据为Map<String, ?>对象时，必须使用此方法进行数据反序列化。
//     * 
//     * @param clazz       对象内的数据类型
//     * @param path        请求地址
//     * @param query       请求URL参数
//     * @param method      请求方式（默认 GET）
//     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
//     * @return            数据对象
//     * @throws IOException
//     */
//    @SuppressWarnings("unchecked")
//    public <T> Map<String, T> map(Class<T> clazz, String path, Map<String, Object> query, Method method, Object data)
//                throws IOException {
//        R r = request(method, path, query, data);
//        if (clazz == null) {
//            return null;
//        }
//        Map<String, T> result = new HashMap<>();
//        for (Entry<String, ?> e : ((Map<String, ?>) r.getData()).entrySet()) {
//            result.put(e.getKey(), fromObject(e.getValue(), clazz));
//        }
//        return result; 
//    }
//    
//    /**
//     * RESTFUL UPDATE　方法：同POST请求。
//     * 
//     * @param path        请求地址
//     * @param query       请求URL参数
//     * @throws IOException
//     */
//    public void update(String path, Map<String, Object> query) throws IOException {
//        update(null, path, null, query);
//    }
//    
//    /**
//     * RESTFUL UPDATE　方法：同POST请求。
//     * 
//     * @param path        请求地址
//     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
//     * @throws IOException
//     */
//    public void update(String path, Object object) throws IOException {
//        update(null, path, object, null);
//    }
//    
//    /**
//     * RESTFUL UPDATE　方法：同POST请求。
//     * 
//     * @param path        请求地址
//     * @param query       请求URL参数
//     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
//     * @return            指定数据类型的对象。注意：如果对象是集合或数组，请使用 list 方法。
//     * @throws IOException
//     */
//    public void update(String path, Object object, Map<String, Object> query) throws IOException {
//        update(null, path, object, query);
//    }
//    
//    /**
//     * RESTFUL UPDATE　方法：同POST请求。
//     * 
//     * @param clazz       数据类型
//     * @param path        请求地址
//     * @param query       请求URL参数
//     * @return            指定数据类型的对象。注意：如果对象是集合或数组，请使用 list 方法。
//     * @throws IOException
//     */
//    public <T> T update(Class<T> clazz, String path, Map<String, Object> query) throws IOException {
//        return update(clazz, path, null, query);
//    }
//    
//    /**
//     * RESTFUL UPDATE　方法：同POST请求。
//     * 
//     * @param clazz       数据类型
//     * @param path        请求地址
//     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
//     * @return            指定数据类型的对象。注意：如果对象是集合或数组，请使用 list 方法。
//     * @throws IOException
//     */
//    public <T> T update(Class<T> clazz, String path, Object data) throws IOException {
//        return update(clazz, path, data, null);
//    }
//    
//    /**
//     * RESTFUL UPDATE　方法：同POST请求。
//     * 
//     * @param clazz       数据类型
//     * @param path        请求地址
//     * @param query       请求URL参数
//     * @param data        请求BODY数据：如果为QueryMapData对象将以form-urlencoded形式添加到BODY中，否则将以JSON形式添加到BODY中。
//     * @return            指定数据类型的对象。注意：如果对象是集合或数组，请使用 list 方法。
//     * @throws IOException
//     */
//	public <T> T update(Class<T> clazz, String path, Object object, Map<String, Object> query) throws IOException {
//        return post(clazz, path, object, query);
//    }
//    
	/**
	 * 将响应内容转换为 R 对象。
	 * @param responseText 请求的响应文本。
	 * @param response     请求的响应对象            
	 * @return
	 */
	protected R transform(String responseText, HttpResponse response) {
       return JsonUtil.fromJson(responseText, R.class);
    }
    
    /**
     * 将数据转换成对象。
     * @param data    数据
     * @param clazz   类型
     * @return
     */
    protected <T> T fromObject(Object data, Class<T> clazz) {
        Gson gson = new GsonBuilder().disableHtmlEscaping()
                .registerTypeAdapter(Date.class, new HttpMessageConverter.GsonCustomerDateJsonSerializer(getDateformat())).create();
        return gson.fromJson(gson.toJson(data), clazz);
    }
}
