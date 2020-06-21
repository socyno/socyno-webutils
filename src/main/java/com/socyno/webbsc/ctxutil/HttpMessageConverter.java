package com.socyno.webbsc.ctxutil;

import com.github.reinert.jjschema.v1.FieldOption;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonDeserializationContext;

import java.io.IOException;
import java.lang.reflect.Type;

import org.apache.commons.lang3.time.DateFormatUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.socyno.base.bscmixutil.ClassUtil;
import com.socyno.base.bscmixutil.JsonUtil;
import com.socyno.base.bscmixutil.StringUtils;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Map;

@Slf4j
public class HttpMessageConverter extends org.springframework.http.converter.json.GsonHttpMessageConverter {
    
    @Getter
    private final static Gson Default = new GsonBuilder().serializeNulls()
            .registerTypeAdapter(Date.class, new GsonCustomerDateJsonSerializer()).create();
    
    @SuppressWarnings("rawtypes")
    private final static TypeAdapter<?> IgnoreDefault = new TypeAdapter() {
        
        @Override
        public void write(JsonWriter out, Object value) throws IOException {
            out.nullValue();
        }
        
        @Override
        public Object read(JsonReader in) throws IOException {
            return null;
        }
    };
    
    public HttpMessageConverter() {
            this(null);
    }
    
    public HttpMessageConverter(Map<String, Object> typeAdapters) {
        GsonBuilder builder = new GsonBuilder()
            .serializeNulls()
            .registerTypeAdapter(Date.class, new GsonCustomerDateJsonSerializer())
            .registerTypeHierarchyAdapter(FieldOption.class, new GsonCustomerStateFieldOptionJsonSerializer());
        if (typeAdapters != null && !typeAdapters.isEmpty()) {
            for (Map.Entry<String, Object> typeAdapter : typeAdapters.entrySet()) {
                String clazz = typeAdapter.getKey();
                Object adapter = typeAdapter.getValue();
                log.info("Try to add gson type adapter : {} => {} "
                                , clazz, adapter);
                boolean withHierarchy = false;
                if (StringUtils.startsWith(clazz, "*")) {
                    withHierarchy = true;
                    clazz = clazz.substring(1);
                }
                if (StringUtils.isBlank(clazz)) {
                    continue;
                }
                try {
                    if (adapter instanceof String) {
                        if (StringUtils.isBlank((String) adapter)) {
                            adapter = IgnoreDefault;
                        } else {
                            adapter = ClassUtil.loadClass((String) adapter).newInstance();
                        }
                    }
                    Class<?> clazzEntity = ClassUtil.loadClass(clazz);
                    if (withHierarchy) {
                        builder.registerTypeHierarchyAdapter(clazzEntity, adapter);
                        continue;
                    }
                    builder.registerTypeAdapter(clazzEntity, adapter);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        super.setGson(builder.create());
    }
    
    public static class GsonCustomerStateFieldOptionJsonSerializer implements JsonSerializer<FieldOption>, JsonDeserializer<FieldOption> {
        @Override
        public FieldOption deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext deserializer)
                throws JsonParseException {
            FieldOption object = null;
            if (json.isJsonPrimitive()) {
                String value;
                if (StringUtils.isBlank(value = ((JsonPrimitive)json).getAsString())) {
                    return null;
                }
                object = JsonUtil.fromObject(new JsonObject(), typeOfT);
                object.setOptionValue(value);
                return object;
            }
            return Default.fromJson(json, typeOfT);
        }
        
        @Override
        public JsonElement serialize(FieldOption obj, Type typeOfT, JsonSerializationContext serializer) {
            if (obj == null) {
                return null;
            }
            JsonElement json = Default.toJsonTree(obj);
            if (json != null && json.isJsonObject()) {
                ((JsonObject)json).addProperty("optionGroup", obj.getOptionGroup());
                ((JsonObject)json).addProperty("optionValue", obj.getOptionValue());
                ((JsonObject)json).addProperty("optionDisplay", obj.getOptionDisplay());
            }
            return json;
        }
    }    
    
    public static class GsonCustomerDateJsonSerializer implements JsonSerializer<Date>, JsonDeserializer<Date> {
        
        private final String dateFormat;
        
        public GsonCustomerDateJsonSerializer() {
            this(null);
        }
        
        public GsonCustomerDateJsonSerializer(String dateFormat) {
            this.dateFormat = StringUtils.ifBlank(dateFormat, "yyyy-MM-dd HH:mm:ssZZ");
        }
        
        @Override
        public JsonElement serialize(Date src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(DateFormatUtils.format(src, 
            		StringUtils.ifBlank(dateFormat, "yyyy-MM-dd HH:mm:ssZZ")));
        }
        
        @Override
        public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }
            if (json.isJsonPrimitive() && ((JsonPrimitive)json).isNumber()) {
                try {
                    return new Date(json.getAsLong());
                } catch(Exception e) {}
            }
            String str = json.getAsString();
            return StringUtils.parseDate(str);    
        }
    }
    
    /**
     * 转换数据为指定的对象实例
     */
    public static <T> T toInstance(Type typeClazz, Object data) {
        Gson gson = null;
        try {
            gson = SpringContextUtil.getBean(HttpMessageConverter.class).getGson();
        } catch (Exception e) {
            gson = Default;
        }
        if (data instanceof CharSequence) {
            return gson.fromJson(data.toString(), typeClazz);
        }
        if (data instanceof JsonElement) {
            return gson.fromJson((JsonElement) data, typeClazz);
        }
        return gson.fromJson(gson.toJson(data), typeClazz);
    }
}
