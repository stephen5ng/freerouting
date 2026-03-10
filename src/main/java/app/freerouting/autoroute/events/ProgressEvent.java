package app.freerouting.autoroute.events;

import java.awt.event.ActionEvent;
import java.time.Instant;

/**
 * Event class for routing progress updates.
 * Contains information about the current routing pass, connections routed,
 * and timing information.
 */
public class ProgressEvent extends ActionEvent {

  private final int passNumber;
  private final int maxPasses;
  private final int connectionsRouted;
  private final int connectionsTotal;
  private final int tracesCreated;
  private final int viasCreated;
  private final long elapsedTimeMs;
  private final String currentActivity;

  public ProgressEvent(
      Object source,
      int passNumber,
      int maxPasses,
      int connectionsRouted,
      int connectionsTotal,
      int tracesCreated,
      int viasCreated,
      long elapsedTimeMs,
      String currentActivity) {
    super(source, ActionEvent.ACTION_PERFORMED, null);
    this.passNumber = passNumber;
    this.maxPasses = maxPasses;
    this.connectionsRouted = connectionsRouted;
    this.connectionsTotal = connectionsTotal;
    this.tracesCreated = tracesCreated;
    this.viasCreated = viasCreated;
    this.elapsedTimeMs = elapsedTimeMs;
    this.currentActivity = currentActivity;
  }

  public int getPassNumber() {
    return passNumber;
  }

  public int getMaxPasses() {
    return maxPasses;
  }

  public int getConnectionsRouted() {
    return connectionsRouted;
  }

  public int getConnectionsTotal() {
    return connectionsTotal;
  }

  public int getTracesCreated() {
    return tracesCreated;
  }

  public int getViasCreated() {
    return viasCreated;
  }

  public long getElapsedTimeMs() {
    return elapsedTimeMs;
  }

  public String getCurrentActivity() {
    return currentActivity;
  }

  public double getCompletionPercentage() {
    return connectionsTotal > 0 ? (100.0 * connectionsRouted / connectionsTotal) : 0.0;
  }
}
