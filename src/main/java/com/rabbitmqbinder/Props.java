package com.rabbitmqbinder;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class Props {
    /* RabbitMQ */
    @Value("${rabbit.queue}")
    private String queue;

    @Value("${rabbit.host}")
    private String host;

    @Value("${rabbit.port}")
    private int port;

    @Value("${rabbit.username}")
    private String username;

    @Value("${rabbit.password}")
    private String password;
}
