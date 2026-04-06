package com.connectedneighbours.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class Statistic {

    private Integer id;

    @JsonProperty("metric_key")
    private String metricKey;

    @JsonProperty("metric_value")
    private Double metricValue;

    private String period;

    @JsonProperty("recorded_at")
    private LocalDateTime recordedAt;

    public Statistic() {
    }

    public Statistic(String metricKey, Double metricValue, String period) {
        this.metricKey = metricKey;
        this.metricValue = metricValue;
        this.period = period;
        this.recordedAt = LocalDateTime.now();
    }

    // Getters et Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getMetricKey() {
        return metricKey;
    }

    public void setMetricKey(String metricKey) {
        this.metricKey = metricKey;
    }

    public Double getMetricValue() {
        return metricValue;
    }

    public void setMetricValue(Double metricValue) {
        this.metricValue = metricValue;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    @Override
    public String toString() {
        return "Statistic{" +
                "id=" + id +
                ", metricKey='" + metricKey + '\'' +
                ", metricValue=" + metricValue +
                ", period='" + period + '\'' +
                ", recordedAt=" + recordedAt +
                '}';
    }
}
