package com.streamlyn.api.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Configuration
@EnableMongoAuditing(dateTimeProviderRef = "dateTimeProvider")
public class MongoConfig {
    @Bean
    public DateTimeProvider dateTimeProvider() {
        return () -> Optional.of(ZonedDateTime.now());
    }

    @Bean
    public MongoCustomConversions customConversions() {
        return new MongoCustomConversions(List.of(
            ZonedDateTimeWriteConverter.INSTANCE,
            ZonedDateTimeReadConverter.INSTANCE
        ));
    }

    static class ZonedDateTimeReadConverter implements Converter<Date, ZonedDateTime> {
        static final ZonedDateTimeReadConverter INSTANCE = new ZonedDateTimeReadConverter();

        public ZonedDateTime convert(Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault());
        }
    }

    static class ZonedDateTimeWriteConverter implements Converter<ZonedDateTime, Date> {
        static final ZonedDateTimeWriteConverter INSTANCE = new ZonedDateTimeWriteConverter();

        public Date convert(ZonedDateTime zonedDateTime) {
            return Date.from(zonedDateTime.toInstant());
        }
    }
}

