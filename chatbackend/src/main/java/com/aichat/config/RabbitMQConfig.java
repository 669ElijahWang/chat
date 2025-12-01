package com.aichat.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    
    // 队列名称
    public static final String CHAT_QUEUE = "chat.queue";
    public static final String CHAT_RESPONSE_QUEUE = "chat.response.queue";
    public static final String NOTIFICATION_QUEUE = "notification.queue";
    
    // 交换机名称
    public static final String CHAT_EXCHANGE = "chat.exchange";
    
    // 路由键
    public static final String CHAT_ROUTING_KEY = "chat.request";
    public static final String CHAT_RESPONSE_ROUTING_KEY = "chat.response";
    public static final String NOTIFICATION_ROUTING_KEY = "notification";
    
    @Bean
    public Queue chatQueue() {
        return QueueBuilder.durable(CHAT_QUEUE).build();
    }
    
    @Bean
    public Queue chatResponseQueue() {
        return QueueBuilder.durable(CHAT_RESPONSE_QUEUE).build();
    }
    
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
    }
    
    @Bean
    public DirectExchange chatExchange() {
        return new DirectExchange(CHAT_EXCHANGE);
    }
    
    @Bean
    public Binding chatBinding() {
        return BindingBuilder
                .bind(chatQueue())
                .to(chatExchange())
                .with(CHAT_ROUTING_KEY);
    }
    
    @Bean
    public Binding chatResponseBinding() {
        return BindingBuilder
                .bind(chatResponseQueue())
                .to(chatExchange())
                .with(CHAT_RESPONSE_ROUTING_KEY);
    }
    
    @Bean
    public Binding notificationBinding() {
        return BindingBuilder
                .bind(notificationQueue())
                .to(chatExchange())
                .with(NOTIFICATION_ROUTING_KEY);
    }
    
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
    
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        return factory;
    }
}

