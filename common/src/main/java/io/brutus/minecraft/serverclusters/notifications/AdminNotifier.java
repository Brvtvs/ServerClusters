package io.brutus.minecraft.serverclusters.notifications;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * Notifies administrators on important, time-sensitive events.
 */
public class AdminNotifier {

  private static final String LOG_PATH = "crashes.log";

  private static final String EMAIL_ORIGIN = "server.clusters.notifications@gmail.com";
  private static final String EMAIL_PASS =
      "x#zEa5ml4o#a2De&*yGSd8I^eO1*AB%8Gs1jK5lSxxIMiNqgMmoG$moNQCm0R7w8";
  private static final String EMAIL_ORIGIN_NAME = "ServerClusters Notifications";

  private final File logFolder;
  private final NotifierConfiguration config;

  /**
   * Class constructor.
   * 
   * @param logFolder The folder to log crash notifications to, if logging is enabled.
   * @param config The configuration for crash notifications.
   */
  public AdminNotifier(File logFolder, NotifierConfiguration config) {
    this.logFolder = logFolder;
    this.config = config;
  }

  /**
   * Attempts to notify administrators of an event.
   * <p>
   * The precise format of the notifications may vary with implementations and configurations.
   * 
   * @param subject A short summary of what the notification is about.
   * @param body The body of the notification. Should contain <b>all</b> information about the
   *        notification, because the subject may or may not appear in certain notification formats.
   *        The body will be prepended with a timestamp of when this method is called.
   */
  public void sendNotification(String subject, String body) {
    Date dt = new Date();
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    String time = "[" + df.format(dt) + "] ";

    System.out.println(time + "[ADMIN NOTIFICATION!!!] " + body);

    body = time + body;

    if (config.isEmailNotificationEnabled()) {
      sendEmails(subject, body);
    }
    if (config.isLoggingEnabled()) {
      log(body);
    }
  }

  private void sendEmails(String subject, String body) {
    for (String address : config.getAdminEmailAddresses()) {
      sendEmail(subject, body, address);
    }
  }

  private void sendEmail(String subject, String body, String targetAddress) {

    // sends TLS-encrypted email via gmail's smtp servers
    Properties props = new Properties();
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.starttls.enable", "true");
    props.put("mail.smtp.host", "smtp.gmail.com");
    props.put("mail.smtp.port", "587");

    Session session = Session.getInstance(props, new javax.mail.Authenticator() {
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(EMAIL_ORIGIN, EMAIL_PASS);
      }
    });

    try {

      Message msg = new MimeMessage(session);
      msg.setFrom(new InternetAddress(EMAIL_ORIGIN, EMAIL_ORIGIN_NAME));
      msg.addRecipient(Message.RecipientType.TO, new InternetAddress(targetAddress,
          "Network Administrator"));
      msg.setSubject(subject);
      msg.setText(body);

      Transport.send(msg);

    } catch (Exception e) {
      System.out.println("[ServerClusters " + getClass().getSimpleName()
          + "] Failed to send an email to " + targetAddress + ".");
      e.printStackTrace();
    }
  }

  private void log(String logMessage) {
    if (logMessage == null || logFolder == null) {
      return;
    }
    try {
      if (!logFolder.exists()) {
        logFolder.mkdir();
      }

      File saveTo = new File(logFolder, LOG_PATH);
      if (!saveTo.exists()) {
        saveTo.createNewFile();
      }

      FileWriter fw = new FileWriter(saveTo, true);
      PrintWriter pw = new PrintWriter(fw);

      pw.println(logMessage);

      pw.flush();
      pw.close();

    } catch (IOException e) {
      System.out.println("[ServerClusters CrashNotifier] Failed to log a message to disk.");
      e.printStackTrace();
    }
  }

}
