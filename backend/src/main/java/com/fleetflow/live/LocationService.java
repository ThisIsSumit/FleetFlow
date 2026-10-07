package com.fleetflow.live;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleetflow.common.*;
import com.fleetflow.dispatch.DriverGeoService;
import com.fleetflow.fleet.FleetRepos.DriverRepository;
import com.fleetflow.outbox.*;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
/** Location pipeline: validate tenant ownership -> Redis GEO -> live fan-out -> Kafka driver.location.updated (partition key = driverId). */
@Service
public class LocationService {
  private static final Logger log = LoggerFactory.getLogger(LocationService.class);
  private final DriverRepository drivers; private final DriverGeoService geo; private final LiveBroadcaster live; private final EventPublisher events; private final ObjectMapper om;
  public LocationService(DriverRepository d, DriverGeoService g, LiveBroadcaster l, EventPublisher e, ObjectMapper om) { drivers = d; geo = g; live = l; events = e; this.om = om; }
  public void update(UUID org, UUID driverId, double lat, double lng) {
    if (lat < -90 || lat > 90 || lng < -180 || lng > 180) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Coordinates out of range");
    drivers.findByIdAndOrganizationId(driverId, org).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Driver not found"));
    try { geo.update(org, driverId, lat, lng); } catch (Exception e) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEO_UNAVAILABLE", "Location index unavailable"); }
    String now = Instant.now().toString();
    try {
      live.publish(org, om.writeValueAsString(Map.of("type", "driver.location", "driverId", driverId, "latitude", lat, "longitude", lng, "timestamp", now)));
      // Telemetry is best-effort and deliberately bypasses the outbox: losing one ping is harmless, a transactional write per ping is not.
      events.publish(Topics.DRIVER_LOCATION, driverId.toString(), om.writeValueAsString(Map.of("eventId", UUID.randomUUID(), "eventType", "DRIVER_LOCATION_UPDATED", "organizationId", org,
          "aggregateId", driverId, "correlationId", Optional.ofNullable(MDC.get(CorrelationFilter.KEY)).orElse(UUID.randomUUID().toString()), "timestamp", now, "payload", Map.of("latitude", lat, "longitude", lng))));
    } catch (Exception e) { log.debug("Location fan-out skipped: {}", e.toString()); }
  }
}
