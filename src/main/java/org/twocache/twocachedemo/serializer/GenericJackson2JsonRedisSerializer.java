//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.twocache.twocachedemo.serializer;

import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import org.springframework.cache.support.NullValue;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.SerializationUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class GenericJackson2JsonRedisSerializer implements RedisSerializer<Object> {
    private final ObjectMapper mapper;

    public GenericJackson2JsonRedisSerializer() {
        this((String)null);
    }

    public GenericJackson2JsonRedisSerializer(String classPropertyTypeName) {
        this(new ObjectMapper());
        this.mapper.registerModule((new SimpleModule()).addSerializer(new NullValueSerializer(classPropertyTypeName)));
        if (StringUtils.hasText(classPropertyTypeName)) {
            this.mapper.enableDefaultTypingAsProperty(DefaultTyping.NON_FINAL, classPropertyTypeName);
        } else {
            this.mapper.enableDefaultTyping(DefaultTyping.NON_FINAL, As.PROPERTY);
        }

    }

    public GenericJackson2JsonRedisSerializer(ObjectMapper mapper) {
        Assert.notNull(mapper, "ObjectMapper must not be null!");
        this.mapper = mapper;
    }

    public byte[] serialize(Object source) throws SerializationException {
        if (source == null) {
           //return SerializationUtils.EMPTY_ARRAY;
            return new byte[0];
        } else {
            try {
                return this.mapper.writeValueAsBytes(source);
            } catch (JsonProcessingException e) {
                throw new SerializationException("Could not write JSON: " + e.getMessage(), e);
            }
        }
    }

    public Object deserialize(byte[] source) throws SerializationException {
        return this.deserialize(source, Object.class);
    }

    public <T> T deserialize(byte[] source, Class<T> type) throws SerializationException {
        Assert.notNull(type, "Deserialization type must not be null! Pleaes provide Object.class to make use of Jackson2 default typing.");
        if (source == null || source.length == 0) {
            return null;
        } else {
            try {
                return (T)this.mapper.readValue(source, type);
            } catch (Exception ex) {
                throw new SerializationException("Could not read JSON: " + ex.getMessage(), ex);
            }
        }
    }

    private class NullValueSerializer extends StdSerializer<NullValue> {
        private static final long serialVersionUID = 1999052150548658808L;
        private final String classIdentifier;

        NullValueSerializer(String classIdentifier) {
            super(NullValue.class);
            this.classIdentifier = StringUtils.hasText(classIdentifier) ? classIdentifier : "@class";
        }

        public void serialize(NullValue value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeStartObject();
            jgen.writeStringField(this.classIdentifier, NullValue.class.getName());
            jgen.writeEndObject();
        }
    }
}
