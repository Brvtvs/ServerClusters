package io.brutus.minecraft.serverclusters.config;

import java.util.Arrays;

import io.brutus.minecraft.serverclusters.notifications.AdminNotifier;
import io.brutus.minecraft.serverclusters.protocol.config.ConfigurationMessage;
import io.brutus.minecraft.serverclusters.protocol.config.ConfigurationRequest;
import io.brutus.networking.pubsubmessager.PubSubMessager;
import io.brutus.networking.pubsubmessager.Subscriber;

/**
 * Loads, maintains, and updates the network-wide configuration.
 * <p>
 * When loading, hangs until a response is received.
 */
public class SharedConfigurationManager {

  private static final long WARMUP = 1500;
  private static final long WAIT_INTERVAL = 100;
  private static final long LOG_INTERVAL = 15000;
  private static final long RESEND_INTERVAL = 10000;
  private static final int FAILS_TO_NOTIFY_ADMIN = 10;

  private final ConfigSubscriber sub;
  private final PubSubMessager messager;
  private ConfigWrapper wrapper;

  private final byte[] requestChannel;
  private final byte[] responseChannel;

  private final long started;
  private volatile boolean waiting;

  /**
   * Class constructor.
   * 
   * @param messager The pub/sub messaging instance on which to request and receive the centralized
   *        network config.
   * @param requestChannel The channel on which to publish configuration requests.
   * @param responseChannel The channel on which to subscribe for configuration responses.
   * @throws IllegalArgumentException On a <code>null</code> or empty parameter.
   */
  public SharedConfigurationManager(PubSubMessager messager, byte[] requestChannel,
      byte[] responseChannel) throws IllegalArgumentException {
    if (messager == null) {
      throw new IllegalArgumentException("messager cannot be null");
    } else if (requestChannel == null || requestChannel.length < 1 || responseChannel == null
        || responseChannel.length < 1) {
      throw new IllegalArgumentException("messaging channels cannot be null or empty");
    }
    this.sub = new ConfigSubscriber();
    this.messager = messager;
    messager.subscribe(responseChannel, sub);
    this.started = System.currentTimeMillis();

    this.requestChannel = requestChannel.clone();
    this.responseChannel = responseChannel.clone();
  }

  /**
   * Loads the central network configuration.
   * <p>
   * Thread-safe, but waits to get a response from a remote server on whatever thread it is run on.
   * Should not be used on any thread that cannot be delayed indefinitely.
   * 
   * @param notifier The admin notifier to use in case the id request cannot be completed and hangs
   *        indefinitely. Can be <code>null</code> not to attempt to notify admins in case of a
   *        continuously unfulfilled request.
   * @return The central network configuration. <code>null</code> if the config could not be loaded.
   */
  public SharedConfiguration loadConfiguration(AdminNotifier notifier) {

    // gives the subscription a chance to actually happen before shooting off a request
    long sinceStarted = System.currentTimeMillis() - started;
    if (sinceStarted < WARMUP) {
      try {
        Thread.sleep(WARMUP - sinceStarted);
      } catch (InterruptedException e) {
        System.out
            .println("[ServerClusters] thread was interrupted while warming up to load the shared configuration");
        e.printStackTrace();
      }
    }

    System.out.println("[ServerClusters] Requesting network config...");
    waiting = true;
    try {

      long timeSinceRequest = Long.MAX_VALUE;
      long timeSinceLog = 0;
      int tries = 0;

      // continually waits and retries until a response is received
      while (waiting) {

        if (timeSinceRequest >= RESEND_INTERVAL) {
          // time to publish a request
          timeSinceRequest = 0;
          tries++;

          if (!messager.publish(requestChannel, ConfigurationRequest.createMessage()).get()) {
            // publishing obviously failed
            System.out
                .println("[ServerClusters] failed to publish a configuration-request message.");
          }
        }

        // periodically logs to clarify why the server might be hanging
        if (timeSinceLog >= LOG_INTERVAL) {
          System.out.println("[ServerClusters] Still waiting for the network config to load...");
          timeSinceLog = 0;
        }

        try {
          Thread.sleep(WAIT_INTERVAL);
          timeSinceRequest += WAIT_INTERVAL;
          timeSinceLog += WAIT_INTERVAL;
        } catch (InterruptedException e) {
          System.out
              .println("[ServerClusters] thread was interrupted while loading the shared configuration. Aborting...");
          e.printStackTrace();
          waiting = false;
        }

        // attempts to send a notification to admins if the request keeps failing
        if (notifier != null && waiting && tries == FAILS_TO_NOTIFY_ADMIN) {
          notifier
              .sendNotification(
                  "ServerClusters Coordinator Unresponsive (config request)",
                  "The ServerClusters coordinator is failing to respond to a request for the centralized config from a server. "
                      + "The coordinator, the message broker, or the network path between them and this server may be down or malfunctioning.");
        }
      }

    } catch (Exception e) {
      System.out.println("[ServerClusters] Failed to load the network config.");
      e.printStackTrace();
    }

    if (wrapper != null) {
      System.out.println("[ServerClusters] Network config loaded.");
    } else {
      System.out.println("[ServerClusters] Could not load network config.");
    }

    return wrapper;
  }

  /**
   * Gets the current configuration.
   * <p>
   * The configuration object may be updated as changes are made elsewhere on the network.
   * 
   * @return The current configuration. <code>null</code> if none has been loaded yet.
   */
  public SharedConfiguration getConfiguration() {
    return wrapper;
  }

  /**
   * Destroys this object and relinquishes its resources.
   * <p>
   * Irreversible.
   */
  public void destroy() {
    messager.unsubscribe(responseChannel, sub);
  }

  private void onConfigurationMessage(ConfigurationMessage config) {
    waiting = false;
    if (wrapper == null) {
      wrapper = new ConfigWrapper(config);
    } else {
      wrapper.setConfiguration(config);
    }
  }

  /**
   * Hides subscriber methods so they cannot be messed with.
   * <p>
   * Listens for configuration messages until destroyed, even those it did not request. Incorporates
   * any updates it gets, such as when a config is pushed from being updated or the coordinator
   * fulfilling another client's request.
   */
  private class ConfigSubscriber implements Subscriber {

    @Override
    public void onMessage(byte[] channel, byte[] message) {
      if (Arrays.equals(channel, responseChannel)) {
        try {
          ConfigurationMessage cm = ConfigurationMessage.fromBytes(message);
          onConfigurationMessage(cm);
        } catch (Exception e) {
          System.out
              .println("[ServerClusters] Received a message on the configuration response channel, but it was not a correctly formatted configuration message");
          e.printStackTrace();
        }
      }
    }
  }

}
