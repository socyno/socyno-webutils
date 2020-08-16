package com.socyno.webbsc.ctxutil;

import com.github.reinert.jjschema.v1.FieldOption;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonDeserializationContext;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

import org.apache.commons.lang3.time.DateFormatUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.socyno.base.bscmixutil.ClassUtil;
import com.socyno.base.bscmixutil.JsonUtil;
import com.socyno.base.bscmixutil.StringUtils;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class HttpMessageConverter extends org.springframework.http.converter.json.GsonHttpMessageConverter {
    
    public static interface GetterSerializeAllowed {
        
    }
    
    @Getter
    private final static Gson Default = new HttpMessageConverter().getGson();
    
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
            .registerTypeAdapter(Long.class, new GsonCustomerLongJsonSerializer())
            .registerTypeAdapter(Integer.class, new GsonCustomerIntegerJsonSerializer())
            .registerTypeAdapter(GetterSerializeAllowed.class, new GsonCustomerGetterJsonSerializer())
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
    
    public static class GsonCustomerStateFieldOptionJsonSerializer
            implements JsonSerializer<FieldOption>, JsonDeserializer<FieldOption> {
        @Override
        public FieldOption deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext deserializer)
                throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }
            FieldOption object = null;
            if (json.isJsonPrimitive()) {
                String value;
                if (StringUtils.isBlank(value = ((JsonPrimitive) json).getAsString())) {
                    return null;
                }
                object = JsonUtil.fromObject(new JsonObject(), typeOfT);
                object.setOptionValue(value);
                return object;
            }
            Object instance = null;
            try {
                Class<?> clazz = (Class<?>) typeOfT;
                clazz.getDeclaredConstructor().newInstance();
                for (Field field : ClassUtil.parseAllFields(clazz)) {
                    if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())
                            || Modifier.isTransient(field.getModifiers())) {
                        continue;
                    }
                    Expose expose;
                    if ((expose = field.getAnnotation(Expose.class)) != null && expose.deserialize()) {
                        continue;
                    }
                    field.setAccessible(true);
                    field.set(instance,
                            deserializer.deserialize(((JsonObject) json).get(field.getName()), field.getType()));
                }
            } catch (JsonParseException e) {
                throw (JsonParseException) e;
            } catch (Exception e) {
                throw new JsonParseException(e);
            }
            return (FieldOption) instance;
        }
        
        @Override
        public JsonElement serialize(FieldOption obj, Type typeOfT, JsonSerializationContext serializer) {
            if (obj == null) {
                return null;
            }
            try {
                Expose expose;
                Map<String, Object> mapped = new HashMap<>();
                for (Field field : ClassUtil.parseAllFields(obj.getClass())) {
                    if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())
                            || Modifier.isTransient(field.getModifiers())) {
                        continue;
                    }
                    if ((expose = field.getAnnotation(Expose.class)) != null && expose.serialize()) {
                        continue;
                    }
                    field.setAccessible(true);
                    mapped.put(field.getName(), field.get(obj));
                }
                JsonElement jsoned = serializer.serialize(mapped);
                if (jsoned != null && jsoned.isJsonObject()) {
                    ((JsonObject)jsoned).addProperty("optionGroup", obj.getOptionGroup());
                    ((JsonObject)jsoned).addProperty("optionValue", obj.getOptionValue());
                    ((JsonObject)jsoned).addProperty("optionDisplay", obj.getOptionDisplay());
                }
                return jsoned;
            } catch (RuntimeException e) {
                throw (RuntimeException) e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    public static class GsonCustomerGetterJsonSerializer implements JsonSerializer<GetterSerializeAllowed> {
        @Override
        public JsonElement serialize(GetterSerializeAllowed obj, Type typeOfSrc, JsonSerializationContext context) {
            if (obj == null) {
                return null;
            }
            try {
                Expose expose;
                Map<String, Object> mapped = new HashMap<>();
                for (Field field : ClassUtil.parseAllFields(obj.getClass())) {
                    if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())
                            || Modifier.isTransient(field.getModifiers())) {
                        continue;
                    }
                    if ((expose = field.getAnnotation(Expose.class)) != null && expose.serialize()) {
                        continue;
                    }
                    field.setAccessible(true);
                    mapped.put(field.getName(), field.get(obj));
                }
                JsonElement jsoned = context.serialize(mapped);
                
                SerializedName serializedName;
                for (Method method : obj.getClass().getMethods()) {
                    if ((serializedName = method.getAnnotation(SerializedName.class)) == null
                            || method.getParameterTypes().length > 0) {
                        continue;
                    }
                    ((JsonObject) jsoned).add(serializedName.value(), context.serialize(method.invoke(method)));
                }
                return jsoned;
            } catch (RuntimeException e) {
                throw (RuntimeException) e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
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
    
    public static class GsonCustomerLongJsonSerializer implements JsonDeserializer<Long> {
        
        @Override
        public Long deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull() || json.isJsonPrimitive() && StringUtils.isBlank(json.getAsString())) {
                return null;
            }
            return json.getAsLong();  
        }
    }
    
    public static class GsonCustomerIntegerJsonSerializer implements JsonDeserializer<Integer> {
        
        @Override
        public Integer deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull() || json.isJsonPrimitive() && StringUtils.isBlank(json.getAsString())) {
                return null;
            }
            return json.getAsInt();  
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
