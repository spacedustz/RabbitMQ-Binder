package com.rabbitmqbinder;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;


@Getter
@AllArgsConstructor
public class OccupancyRawEvent {
    private String name;
    private int count;
    private BigDecimal score;
    private long timestamp; // 이벤트 발생 시간 (epoch millis)
}
