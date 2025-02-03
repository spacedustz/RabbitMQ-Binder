package com.rabbitmqbinder;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class Occupancy15SecStatDto {
    // 집계 기준 시간 레이블 (예: 윈도우 시작 시각을 문자열로 표현)
    private String timestampLabel;

    // 첫 이벤트의 name 값을 그대로 사용 (여러 이벤트 그룹을 구분할 필요가 있다면 추가 로직 필요)
    private String name;

    // 15초 동안의 총 count 및 총 score
    private int totalCount;
    private BigDecimal totalScore;
    private String routingKey;

    @Override
    public String toString() {
        return "Occupancy15SecStatDto{" +
                "timestampLabel='" + timestampLabel + '\'' +
                ", name='" + name + '\'' +
                ", totalCount=" + totalCount +
                ", totalScore=" + totalScore +
                ", routingKey='" + routingKey + '\'' +
                '}';
    }
}
