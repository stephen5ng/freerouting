package app.freerouting.autoroute.events;

/**
 * Listener interface for routing progress events.
 * Components (GUI, CLI, API) can implement this to receive real-time updates.
 */
public interface ProgressEventListener {

  /**
   * Called when routing progress is made.
   *
   * @param event The progress event containing updated statistics
   */
  void onProgressEvent(ProgressEvent event);
}
