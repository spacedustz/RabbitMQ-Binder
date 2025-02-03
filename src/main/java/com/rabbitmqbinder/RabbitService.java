package com.rabbitmqbinder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitService {
    private final Map<Integer, ConnectionFactory> connectionFactoryMap = new ConcurrentHashMap<>();
    private final Map<Integer, Connection> connectionMap = new ConcurrentHashMap<>();
    private final Map<Integer, List<Channel>> channelMap = new ConcurrentHashMap<>();
    private final Map<Integer, String> queueNameMap = new ConcurrentHashMap<>();

    private final Props props;
    private final ObjectMapper objectMapper;

    // 기존에 사용하던 DeliverCallBack 대신 StreamBridge를 주입하여 메시지 전달에 사용
    private final StreamBridge streamBridge;

    @PostConstruct
    public void init() {
        log.info("==================== RabbitMQ Connection 초기화 시작 ====================");
        this.connectRabbitMQ();

        // 채널과 큐 이름을 조회하여 consume 메서드 호출
        List<Channel> channelList = channelMap.get(1);
        String queueName = queueNameMap.get(1);
        if (channelList != null && !channelList.isEmpty()) {
            // 생성된 첫번째 채널로 메시지 소비 시작
            this.consume(channelList.get(0), queueName);
        } else {
            log.warn("채널이 생성되지 않았습니다.");
        }

        log.info("==================== RabbitMQ Connection 초기화 완료 ====================");
    }

    public void consume(final Channel channelParam, String queueName) {
        try {
            DeliverCallback deliveryCallback = (consumerTag, delivery) -> {
                try {
                    // 수신한 메시지를 문자열로 읽음
                    String messageBody = new String(delivery.getBody(), StandardCharsets.UTF_8);
                    log.info("RabbitService 수신 메시지: {}", messageBody);

                    // RabbitMQ의 실제 라우팅 키 추출
                    String routingKey = delivery.getEnvelope().getRoutingKey();

                    // MessageBuilder를 사용하여 헤더에 "amqp_receivedRoutingKey" 추가
                    Message<String> message = MessageBuilder.withPayload(messageBody)
                            .setHeader("amqp_receivedRoutingKey", routingKey)
                            .build();

                    // StreamBridge를 이용하여 Spring Cloud Stream의 입력 채널("aggregateAndLog-in-0")로 메시지 전달
                    boolean sent = streamBridge.send("aggregateAndLog-in-0", message);

                    if (!sent) log.warn("StreamBridge를 통한 메시지 전달 실패");
                } catch (Exception e) {
                    log.error("메시지 처리 중 에러 발생: {}", e.getMessage(), e);
                }
            };

            channelParam.basicConsume(queueName, true, deliveryCallback, consumerTag -> {
                log.info("Consumer 취소됨: {}", consumerTag);
            });
        } catch (Exception e) {
            log.error("[Consume Queue] Consume Failed - Exception : {}, Cause : {}", e.getMessage(), e.getCause());
        }
    }

    /* RabbitMQ Connection & Channel 생성 */
    private void connectRabbitMQ() {
        // TODO 1: Queue Name을 Map에 넣기
        queueNameMap.put(1, props.getQueue());
        log.info("RabbitMQ Queue 등록 - Queue Name : {}", props.getQueue());

        // TODO 2: Connection Factory 생성 (1개만 필요)
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(props.getHost());
        factory.setPort(props.getPort());
        factory.setUsername(props.getUsername());
        factory.setPassword(props.getPassword());
        connectionFactoryMap.put(1, factory);
        log.info("RabbitMQ Connection Factory Created - Host : {}, Port : {}", props.getHost(), props.getPort());

        // TODO 3: Connection Factory에서 Connection을 1개만 만들기
        connectionFactoryMap.forEach((key, connectionFactory) -> {
            Connection connection = null;
            try {
                connection = factory.newConnection();
                connectionMap.put(1, connection);
                log.info("RabbitMQ Connection Created");
            } catch (Exception e) {
                log.error("RabbitMQ Connection 생성 실패 - {}", e.getMessage());
            }

            // TODO 3-1: 이미 채널이 오픈되어 있다면 채널 종료
            try {
                List<Channel> channels = channelMap.get(1);

                if (channels != null && channels.size() > 0) {
                    channels.stream().forEach(channel -> {
                        if (channel != null && channel.isOpen()) {
                            try {
                                channel.close();
                            } catch (Exception e) {
                                log.warn("Create RabbitMQ Connect & Channel Close Error - {}", e.getMessage());
                            }
                        }
                    });
                    channelMap.remove(1);
                }

                // TODO 3-2: 1개의 Connection에 QueueNameMap의 숫자만큼 채널 생성
                List<Channel> channelList = new ArrayList<>();

//                for (int i = 1; i <= props.getInstances().size(); i++) {
//                    Channel channel = connection.createChannel();
//                    channelList.add(channel);
//                    log.info("RabbitMQ Channel {} Created", i);
//                }

                Channel channel = connection.createChannel();
                channelList.add(channel);

                channelMap.put(1, channelList);


            } catch (Exception e) {
                log.error("Rabbit Connection Failed : {}", e.getMessage());
                e.printStackTrace();
            }

        });
    }
}
