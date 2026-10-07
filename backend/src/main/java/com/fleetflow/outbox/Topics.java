package com.fleetflow.outbox;
public final class Topics {
  public static final String ORDER_CREATED = "order.created", DISPATCH_REQUESTED = "dispatch.requested", DRIVER_ASSIGNED = "driver.assigned",
      DRIVER_LOCATION = "driver.location.updated", DELIVERY_STARTED = "delivery.started", DELIVERY_COMPLETED = "delivery.completed",
      DELIVERY_CANCELLED = "delivery.cancelled", DRIVER_UNAVAILABLE = "driver.unavailable", NOTIFICATION = "notification.requested";
  private Topics() {}
}
