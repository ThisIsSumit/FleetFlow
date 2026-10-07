package com.fleetflow.outbox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OutboxRelayTest {
  @Test void backoffIsExponentialAndCapped() {
    assertEquals(2, OutboxRelay.backoffSeconds(1));
    assertEquals(8, OutboxRelay.backoffSeconds(3));
    assertEquals(300, OutboxRelay.backoffSeconds(9));
    assertEquals(300, OutboxRelay.backoffSeconds(50));
  }
}
