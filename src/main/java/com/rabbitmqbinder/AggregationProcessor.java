package com.rabbitmqbinder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
public class AggregationProcessor {
    /**
     * 메시지 헤더에 있는 Routing Key를 기준으로 그룹화한 후,
     * 각 그룹에 대해 15초 동안 들어온 raw data의 수치 연산 후 집계를 수행하는 함수.
     * 입력: Message<OccupancyRawEvent> 메시지 스트림
     * 출력: 집계 결과(Occupancy15SecStatDto)를 로그에 출력 (외부 발행 X 로그로만 찍음)
     */
    @Bean
    public Consumer<Flux<Message<OccupancyRawEvent>>> aggregateAndLog() {
        return flux -> flux
                .doOnNext(message -> {
                    String headerValue = message.getHeaders().get("amqp_receivedRoutingKey", String.class);
                    log.info("Received message with Routing Key : {}", headerValue);
                })
                // 헤더 "amqp_receivedRoutingKey"를 기준으로 그룹화
                .groupBy(message -> message.getHeaders().get("amqp_receivedRoutingKey", String.class))
                .flatMap(groupedFlux ->
                        groupedFlux
                                // 각 그룹별 15초 윈도우 생성
                                .window(Duration.ofSeconds(15))
                                .flatMap(window -> window.collectList()
                                        .filter(list -> !list.isEmpty())
                                        .map(list -> aggregateByRoutingKey2(list, groupedFlux.key()))
                                )
                )
                .subscribe(aggregated -> log.info("15초 데이터 생성 완료 / RoutingKey: {}, count: {}, score: {}",
                        aggregated.getRoutingKey(), aggregated.getTotalCount(), aggregated.getTotalScore()));
    }

    /**
     * 주어진 메시지 리스트(15초 윈도우)를 집계하여 Occupancy15SecStatDto를 생성.
     * @param messages 15초 동안 수신된 메시지 리스트
     * @param routingKey 해당 그룹의 Routing Key
     * @return 집계된 DTO
     */
    private Occupancy15SecStatDto aggregateByRoutingKey(List<Message<OccupancyRawEvent>> messages, String routingKey) {
        Occupancy15SecStatDto aggregated = new Occupancy15SecStatDto();

        // 첫 메시지의 payload에서 name 사용 (필요시 다른 로직 추가)
        OccupancyRawEvent first = messages.get(0).getPayload();
        aggregated.setName(first.getName());
        aggregated.setRoutingKey(routingKey);

        // 15초 동안의 총 count, score 합산
        int totalCount = messages.stream()
                .mapToInt(m -> m.getPayload().getCount())
                .sum();

        BigDecimal totalScore = messages.stream()
                .map(m -> m.getPayload().getScore())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        aggregated.setTotalCount(totalCount);
        aggregated.setTotalScore(totalScore);

        // 윈도우 내 최소 timestamp를 기준으로 집계 시간 레이블 생성
        long windowStart = messages.stream()
                .mapToLong(m -> m.getPayload().getTimestamp())
                .min()
                .orElse(System.currentTimeMillis());
        Date date = new Date(windowStart);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        aggregated.setTimestampLabel(sdf.format(date));

        return aggregated;
    }

    /**
     * 주어진 메시지 리스트(15초)의 수치를 연산 후 집계하여 Occupancy15SecStatDto를 생성.
     * @param messages 15초 동안 수신된 메시지 리스트
     * @param routingKey 해당 그룹의 Routing Key
     * @return 집계된 DTO
     */
    private Occupancy15SecStatDto aggregateByRoutingKey2(List<Message<OccupancyRawEvent>> messages, String routingKey) {
        Occupancy15SecStatDto aggregated = new Occupancy15SecStatDto();

        // 첫 메시지의 payload에서 name 사용 (필요시 다른 로직 추가)
        OccupancyRawEvent first = messages.get(0).getPayload();
        aggregated.setName(first.getName());
        aggregated.setRoutingKey(routingKey);

        // 15초 동안의 총 count, score 합산 (기존 단순 합산)
        int totalCount = messages.stream()
                .mapToInt(m -> m.getPayload().getCount())
                .sum();

        BigDecimal totalScore = messages.stream()
                .map(m -> m.getPayload().getScore())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        try {
            long startTime = System.currentTimeMillis();
            double dummy = 0.0;
            while (System.currentTimeMillis() - startTime < 1000) {
                dummy += Math.sin(totalCount) * Math.log(totalScore.doubleValue() + 1) * Math.random();
            }
            totalScore = totalScore.add(BigDecimal.valueOf(dummy % 10));
        } catch (Exception e) {
            // 예외 발생 시 현재 스레드 인터럽트
            Thread.currentThread().interrupt();
        }

        aggregated.setTotalCount(totalCount);
        aggregated.setTotalScore(totalScore);

        // 윈도우 내 최소 timestamp를 기준으로 집계 시간 레이블 생성
        long windowStart = messages.stream()
                .mapToLong(m -> m.getPayload().getTimestamp())
                .min()
                .orElse(System.currentTimeMillis());
        Date date = new Date(windowStart);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        aggregated.setTimestampLabel(sdf.format(date));

        return aggregated;
    }
}
