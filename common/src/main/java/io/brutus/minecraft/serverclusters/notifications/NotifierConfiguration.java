package io.brutus.minecraft.serverclusters.notifications;

import java.util.List;

/**
 * A configuration for how to notify administrators of possible crashes on the network.
 */
public interface NotifierConfiguration {

  /**
   * Gets whether notifications should be logged to disk.
   * 
   * @return <code>true</code> to log notifications, else <code>false</code>.
   */
  boolean isLoggingEnabled();

  /**
   * Gets whether notifications should be emailed to admins.
   * 
   * @return <code>true</code> to email notifications, else <code>false</code>.
   */
  boolean isEmailNotificationEnabled();

  /**
   * Gets the email addresses of admins who should be notified of crashes.
   * 
   * @return The emails to inform of crashes, if the feature is enabled.
   */
  List<String> getAdminEmailAddresses();

}
