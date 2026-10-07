package com.fleetflow.outbox;
public interface EventPublisher { void publish(String topic, String key, String json) throws Exception; }
