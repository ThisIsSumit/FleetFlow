# ADR: Kafka event architecture

## Context
Dispatch, notifications and analytics should react to order changes without blocking the request.

## Decision
Domain events on Kafka topics keyed by orderId, a uniform envelope with correlationId, at-least-once delivery, idempotent consumers, retry then DLQ.

## Alternatives
Synchronous calls between modules; database polling only; RabbitMQ/SQS.

## Trade-offs
Operational overhead of Kafka and eventual consistency (an order is briefly CREATED before dispatch). Buys decoupling, replay and per-order ordering.
