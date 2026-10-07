package com.fleetflow.seed;
import com.fleetflow.dispatch.DriverGeoService;
import com.fleetflow.domain.*;
import com.fleetflow.domain.Repos.*;
import com.fleetflow.fleet.Entities.*;
import com.fleetflow.fleet.Enums.*;
import com.fleetflow.fleet.FleetRepos.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
/** Fictional demo tenant with realistic Indian logistics data. Enabled with SEED_DEMO=true. Password comes from DEMO_PASSWORD (a random one is logged once if unset). */
@Component @ConditionalOnProperty(name = "fleetflow.seed.enabled", havingValue = "true")
public class DemoSeeder implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
  record Area(String city, String name, String plate, double lat, double lng) {}
  static final List<Area> AREAS = List.of(
      new Area("Chandigarh", "Sector 17", "CH01", 30.7398, 76.7827), new Area("Chandigarh", "Sector 35", "CH01", 30.7196, 76.7583),
      new Area("Mohali", "Phase 5", "PB65", 30.7046, 76.7179), new Area("Mohali", "Sector 70", "PB65", 30.7063, 76.6890),
      new Area("Panchkula", "Sector 5", "HR03", 30.6942, 76.8606), new Area("Panchkula", "Sector 20", "HR03", 30.6870, 76.8500),
      new Area("Delhi", "Connaught Place", "DL01", 28.6315, 77.2167), new Area("Delhi", "Saket", "DL01", 28.5245, 77.2066),
      new Area("Gurgaon", "Cyber Hub", "HR26", 28.4950, 77.0890), new Area("Gurgaon", "Sector 56", "HR26", 28.4230, 77.1060),
      new Area("Bengaluru", "Koramangala", "KA01", 12.9352, 77.6245), new Area("Bengaluru", "Indiranagar", "KA01", 12.9784, 77.6408),
      new Area("Mumbai", "Andheri East", "MH02", 19.1197, 72.8468), new Area("Mumbai", "Bandra West", "MH02", 19.0596, 72.8295));
  static final String[] NAMES = {"Aarav Khanna", "Ishaan Verma", "Kabir Malhotra", "Meera Sethi", "Nisha Bedi", "Rohan Chawla", "Simran Gill", "Tanvi Arora", "Uday Randhawa", "Vivaan Kapoor", "Yash Bhatia", "Zoya Sandhu"};
  static final int[] HOME = {0, 2, 4, 1, 3, 5, 6, 7, 8, 9, 10, 12};
  static final VehicleType[] TYPES = {VehicleType.BIKE, VehicleType.BIKE, VehicleType.CAR, VehicleType.BIKE, VehicleType.VAN, VehicleType.BIKE, VehicleType.CAR, VehicleType.BIKE, VehicleType.TRUCK, VehicleType.BIKE, VehicleType.CAR, VehicleType.VAN};
  static final String[] CUSTOMERS = {"Anaya Trading Co", "Bhatia Pharmacy", "Cedar Home Store", "Dhillon Electronics", "Eshaan Bakery", "Fresh Basket", "Gulmohar Books", "Hari Om Sweets", "Indus Hardware", "Jaipur Textiles"};
  static final String[] ITEMS = {"Documents", "Grocery box", "Medicine pack", "Electronics parcel", "Bakery order", "Clothing parcel", "Books"};
  private final UserRepository users; private final OrganizationRepository orgs; private final VehicleRepository vehicles; private final DriverRepository drivers;
  private final OrderRepository orders; private final OrderEventRepository events; private final PasswordEncoder enc; private final DriverGeoService geo; private final TransactionTemplate tx; private final String configured;
  public DemoSeeder(UserRepository u, OrganizationRepository o, VehicleRepository v, DriverRepository d, OrderRepository or, OrderEventRepository e, PasswordEncoder enc, DriverGeoService geo, TransactionTemplate tx, @Value("${fleetflow.seed.password}") String pw) {
    users = u; orgs = o; vehicles = v; drivers = d; orders = or; events = e; this.enc = enc; this.geo = geo; this.tx = tx; configured = pw;
  }
  @Override public void run(ApplicationArguments args) {
    if (users.existsByEmailIgnoreCase("demo@fleetflow.dev")) return;
    String pw = configured == null || configured.isBlank() ? randomPassword() : configured;
    if (configured == null || configured.isBlank()) log.warn("DEMO_PASSWORD not set. Generated demo password for demo@fleetflow.dev / dispatcher@fleetflow.dev / admin@fleetflow.dev: {}", pw);
    var placed = new ArrayList<Object[]>();
    tx.executeWithoutResult(s -> seed(pw, placed));
    for (Object[] p : placed) try { geo.update((UUID) p[0], (UUID) p[1], (double) p[2], (double) p[3]); } catch (Exception e) { log.warn("Redis unavailable, skipped driver positions: {}", e.toString()); break; }
    log.info("Demo data ready. Login: demo@fleetflow.dev");
  }
  private static String randomPassword() { var r = new SecureRandom(); var sb = new StringBuilder("Demo-"); for (int i = 0; i < 12; i++) sb.append("abcdefghjkmnpqrstuvwxyz23456789".charAt(r.nextInt(31))); return sb.toString(); }
  private void seed(String pw, List<Object[]> placed) {
    var org = orgs.save(new Organization("Demo Logistics Pvt Ltd")); UUID orgId = org.getId(); String hash = enc.encode(pw);
    for (var t : new String[][]{{"Aditi Sharma", "demo@fleetflow.dev", "OWNER"}, {"Karan Mehta", "dispatcher@fleetflow.dev", "DISPATCHER"}, {"Neha Kapoor", "admin@fleetflow.dev", "ADMIN"}}) {
      var u = new AppUser(orgId, t[0], t[1], hash, Role.valueOf(t[2])); u.setEmailVerified(true); users.save(u);
    }
    var rnd = new Random(42); var ds = new ArrayList<Driver>(); var home = new ArrayList<Area>();
    for (int i = 0; i < NAMES.length; i++) {
      var a = AREAS.get(HOME[i]); var v = new Vehicle(); v.organizationId = orgId; v.type = TYPES[i]; v.registrationNumber = String.format("%sAB%04d", a.plate(), 1200 + i);
      v.capacityKg = switch (v.type) { case BIKE -> 20; case CAR -> 150; case VAN -> 800; case TRUCK -> 5000; }; vehicles.save(v);
      var d = new Driver(); d.organizationId = orgId; d.name = NAMES[i]; d.phone = "+91 98" + String.format("%08d", 10000000 + i * 7919); d.vehicleId = v.id;
      d.rating = BigDecimal.valueOf(Math.round((4.0 + rnd.nextDouble()) * 100) / 100.0); d.status = i == 10 ? DriverStatus.OFFLINE : i == 11 ? DriverStatus.ON_BREAK : DriverStatus.AVAILABLE;
      drivers.save(d); ds.add(d); home.add(a);
    }
    var du = new AppUser(orgId, NAMES[0], "driver@fleetflow.dev", hash, Role.DRIVER); du.setEmailVerified(true); users.save(du); ds.get(0).userId = du.getId(); // demo driver login, linked to the first driver
    Instant now = Instant.now(); int[] activeDriver = {0, 1, 3}; // drivers that hold the three in-flight orders
    for (int i = 0; i < 60; i++) {
      double ageH = (59 - i) * 168.0 / 60 + rnd.nextDouble(); Instant t0 = now.minusSeconds((long) (ageH * 3600));
      OrderStatus st; Driver d = null; Area pick;
      if (i >= 56) { st = new OrderStatus[]{OrderStatus.DISPATCH_REQUESTED, OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT, OrderStatus.ARRIVED}[i - 56];
        if (st != OrderStatus.DISPATCH_REQUESTED) d = ds.get(activeDriver[i - 57]); pick = d != null ? home.get(ds.indexOf(d)) : AREAS.get(1); }
      else { double r = rnd.nextDouble(); st = r < 0.78 ? OrderStatus.DELIVERED : r < 0.88 ? OrderStatus.CANCELLED : OrderStatus.FAILED;
        d = ds.get(rnd.nextInt(10)); pick = home.get(ds.indexOf(d)); if (st == OrderStatus.CANCELLED && rnd.nextBoolean()) d = null; }
      var drop = AREAS.stream().filter(a -> a.city().equals(pick.city()) && a != pick).findFirst().orElse(pick);
      var o = new DeliveryOrder(); o.organizationId = orgId; o.customerName = CUSTOMERS[rnd.nextInt(CUSTOMERS.length)]; o.customerPhone = "+91 97" + String.format("%08d", 20000000 + i * 104729 % 9999999);
      o.pickupAddress = pick.name() + ", " + pick.city(); o.pickupLat = pick.lat(); o.pickupLng = pick.lng(); o.dropAddress = drop.name() + ", " + drop.city(); o.dropLat = drop.lat() + 0.004; o.dropLng = drop.lng() + 0.004;
      o.items = "[{\"name\":\"" + ITEMS[rnd.nextInt(ITEMS.length)] + "\",\"quantity\":" + (1 + rnd.nextInt(3)) + "}]";
      o.priority = Priority.values()[rnd.nextInt(4)]; o.createdAt = t0; o.status = st; o.driverId = d == null ? null : d.id;
      var evs = new ArrayList<OrderEvent>(); Instant t = t0; OrderStatus prev = OrderStatus.CREATED; evs.add(mk(o, null, OrderStatus.CREATED, t));
      OrderStatus[] path = switch (st) { case DISPATCH_REQUESTED -> new OrderStatus[]{OrderStatus.DISPATCH_REQUESTED}; case ASSIGNED -> new OrderStatus[]{OrderStatus.DISPATCH_REQUESTED, OrderStatus.ASSIGNED};
        case ARRIVED -> new OrderStatus[]{OrderStatus.DISPATCH_REQUESTED, OrderStatus.ASSIGNED, OrderStatus.ARRIVED}; case IN_TRANSIT -> new OrderStatus[]{OrderStatus.DISPATCH_REQUESTED, OrderStatus.ASSIGNED, OrderStatus.ARRIVED, OrderStatus.IN_TRANSIT};
        case DELIVERED -> new OrderStatus[]{OrderStatus.DISPATCH_REQUESTED, OrderStatus.ASSIGNED, OrderStatus.ARRIVED, OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED};
        case FAILED -> new OrderStatus[]{OrderStatus.DISPATCH_REQUESTED, OrderStatus.ASSIGNED, OrderStatus.ARRIVED, OrderStatus.IN_TRANSIT, OrderStatus.FAILED};
        default -> d == null ? new OrderStatus[]{OrderStatus.DISPATCH_REQUESTED, OrderStatus.CANCELLED} : new OrderStatus[]{OrderStatus.DISPATCH_REQUESTED, OrderStatus.ASSIGNED, OrderStatus.CANCELLED}; };
      for (OrderStatus next : path) { t = t.plusSeconds(switch (next) { case DISPATCH_REQUESTED -> 1; case ASSIGNED -> 8 + rnd.nextInt(30); case ARRIVED -> 300 + rnd.nextInt(420); case IN_TRANSIT -> 120 + rnd.nextInt(240); default -> 720 + rnd.nextInt(1500); }); evs.add(mk(o, prev, next, t)); prev = next; }
      o.updatedAt = t; orders.save(o); events.saveAll(evs); // order first: order_events has a foreign key to it
      if (st == OrderStatus.ASSIGNED || st == OrderStatus.ARRIVED || st == OrderStatus.IN_TRANSIT) d.status = DriverStatus.BUSY;
    }
    ds.forEach(drivers::save);
    for (int i = 0; i < ds.size(); i++) if (ds.get(i).status != DriverStatus.OFFLINE) placed.add(new Object[]{orgId, ds.get(i).id, home.get(i).lat() + (rnd.nextDouble() - 0.5) * 0.01, home.get(i).lng() + (rnd.nextDouble() - 0.5) * 0.01});
  }
  private OrderEvent mk(DeliveryOrder o, OrderStatus from, OrderStatus to, Instant at) {
    var e = new OrderEvent(); e.organizationId = o.organizationId; e.orderId = o.id; e.fromStatus = from; e.toStatus = to; e.eventId = UUID.randomUUID(); e.createdAt = at; return e;
  }
}
