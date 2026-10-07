package com.fleetflow.fleet;
public class Enums {
  public enum VehicleType { BIKE, CAR, VAN, TRUCK }
  public enum VehicleStatus { ACTIVE, MAINTENANCE, INACTIVE }
  public enum DriverStatus { AVAILABLE, BUSY, OFFLINE, ON_BREAK }
  public enum Priority { LOW, NORMAL, HIGH, URGENT }
  public enum OrderStatus { CREATED, DISPATCH_REQUESTED, ASSIGNED, ARRIVED, IN_TRANSIT, DELIVERED, CANCELLED, FAILED }
}
