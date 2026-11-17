package com.example.dependencyscanner.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    @Bean
    Queue scanQueue(@Value("${scanner.scan-queue}") String name) {
        return new Queue(name, true);
    }

    @Bean
    TopicExchange resultExchange(@Value("${scanner.result-exchange}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    MessageConverter rabbitMessageConverter() {
        var converter = new Jackson2JsonMessageConverter();
        var typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.example.dependencyscanner.model");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
