package com.fleetflow.fleet;
import com.fleetflow.common.ApiException;
import org.junit.jupiter.api.Test;
import static com.fleetflow.fleet.Enums.OrderStatus.*;
import static org.junit.jupiter.api.Assertions.*;
class OrderStateMachineTest {
  @Test void happyPath() {
    var path = new Enums.OrderStatus[]{CREATED, DISPATCH_REQUESTED, ASSIGNED, ARRIVED, IN_TRANSIT, DELIVERED};
    for (int i = 0; i < path.length - 1; i++) assertTrue(OrderStateMachine.canTransition(path[i], path[i + 1]), path[i] + "->" + path[i + 1]);
  }
  @Test void terminalStatesAreFinal() {
    for (var t : new Enums.OrderStatus[]{DELIVERED, CANCELLED, FAILED})
      for (var to : Enums.OrderStatus.values()) assertFalse(OrderStateMachine.canTransition(t, to));
  }
  @Test void cannotSkipSteps() {
    assertFalse(OrderStateMachine.canTransition(CREATED, DELIVERED));
    assertFalse(OrderStateMachine.canTransition(ASSIGNED, DELIVERED));
    assertThrows(ApiException.class, () -> OrderStateMachine.require(IN_TRANSIT, CANCELLED));
  }
  @Test void reassignmentAllowedBeforePickup() { assertTrue(OrderStateMachine.canTransition(ASSIGNED, DISPATCH_REQUESTED)); }
}
