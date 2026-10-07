package com.fleetflow.fleet;
import com.fleetflow.common.ApiException;
import com.fleetflow.fleet.Enums.OrderStatus;
import org.springframework.http.HttpStatus;
import java.util.*;
import static com.fleetflow.fleet.Enums.OrderStatus.*;
public final class OrderStateMachine {
  private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);
  static {
    ALLOWED.put(CREATED, EnumSet.of(DISPATCH_REQUESTED, CANCELLED));
    ALLOWED.put(DISPATCH_REQUESTED, EnumSet.of(ASSIGNED, CANCELLED, FAILED));
    ALLOWED.put(ASSIGNED, EnumSet.of(ARRIVED, DISPATCH_REQUESTED, CANCELLED)); // back to DISPATCH_REQUESTED = reassignment
    ALLOWED.put(ARRIVED, EnumSet.of(IN_TRANSIT, CANCELLED));
    ALLOWED.put(IN_TRANSIT, EnumSet.of(DELIVERED, FAILED));
    for (var t : EnumSet.of(DELIVERED, CANCELLED, FAILED)) ALLOWED.put(t, EnumSet.noneOf(OrderStatus.class));
  }
  private OrderStateMachine() {}
  public static boolean canTransition(OrderStatus from, OrderStatus to) { return ALLOWED.get(from).contains(to); }
  public static void require(OrderStatus from, OrderStatus to) {
    if (!canTransition(from, to)) throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Cannot move order from " + from + " to " + to);
  }
}
