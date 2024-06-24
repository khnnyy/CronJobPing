package armscronping;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.*;
import javax.mail.internet.*;

public class ArmsCronPing {

    private static final Logger logger = Logger.getLogger(ArmsCronPing.class.getName());
    private static int timeout;
    private static int threadPoolSize;
    private static String rfidsFilePath;
    private static Session emailSession;
    private static String emailSender;
    private static String emailRecipient;
    private static List<String> offlineRfids = new ArrayList<>();

    public static void main(String[] args) {
        // Load email configuration
        emailSender = "notmycandy56@gmail.com"; // Replace with your sender email
        emailRecipient = "xabubakarzx@gmail.com"; // Replace with your recipient email
        String emailHost = "smtp.gmail.com";
        String emailPassword = "nzgx yqhz gigr amjh"; // Replace with your email password

        // Set up email properties
        Properties emailProperties = new Properties();
        emailProperties.setProperty("mail.smtp.host", emailHost);
        emailProperties.setProperty("mail.smtp.port", "587");
        emailProperties.setProperty("mail.smtp.auth", "true");
        emailProperties.setProperty("mail.smtp.starttls.enable", "true");

        // Authenticator object for email authentication
        Authenticator authenticator = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(emailSender, emailPassword);
            }
        };

        // Create email session
        emailSession = Session.getInstance(emailProperties, authenticator);

        // Load configuration for pings
        loadConfig("config.properties");

        // Load device IPs
        List<String> rfids = loadRfidIPs(rfidsFilePath);
        if (rfids == null || rfids.isEmpty()) {
            logger.severe("No devices found to ping.");
            return;
        }

        // Create thread pool and ping devices
        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        for (String rfid : rfids) {
            executorService.execute(() -> pingRfid(rfid));
        }

        // Shutdown thread pool
        executorService.shutdown();
        try {
            executorService.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Error waiting for task completion", e);
        }

        // Send email if there are offline RFIDs
        if (!offlineRfids.isEmpty()) {
            StringBuilder emailBody = new StringBuilder("The following RFID devices are offline:\n");
            for (String offlineRfid : offlineRfids) {
                emailBody.append(offlineRfid).append("\n");
            }
            sendEmail(emailRecipient, "RFID Offline Alert", emailBody.toString());
        }
    }

    private static void pingRfid(String rfid) {
        try {
            InetAddress inet = InetAddress.getByName(rfid);
            boolean status = inet.isReachable(timeout);
            logger.info("RFID " + rfid + " is " + (status ? "online" : "offline"));
            if (!status) {
                synchronized (offlineRfids) {
                    offlineRfids.add(rfid);
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error pinging RFID " + rfid, e);
        }
    }

    private static List<String> loadRfidIPs(String filePath) {
        List<String> rfids = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(filePath)) {
            Properties properties = new Properties();
            properties.load(fis);
            for (String key : properties.stringPropertyNames()) {
                rfids.add(properties.getProperty(key));
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error loading device IPs from " + filePath, e);
        }
        return rfids;
    }

    private static void loadConfig(String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            Properties properties = new Properties();
            properties.load(fis);
            timeout = Integer.parseInt(properties.getProperty("timeout", "5000"));
            threadPoolSize = Integer.parseInt(properties.getProperty("threadPoolSize", "10"));
            rfidsFilePath = properties.getProperty("rfidsFilePath");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error loading configuration", e);
            timeout = 5000;
            threadPoolSize = 4;
        }
    }

    private static void sendEmail(String recipient, String subject, String body) {
        try {
            MimeMessage message = new MimeMessage(emailSession);
            message.setFrom(new InternetAddress(emailSender));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
            message.setSubject(subject);
            message.setText(body);
            Transport.send(message);
            logger.info("Email sent successfully to " + recipient);
        } catch (MessagingException mex) {
            logger.log(Level.SEVERE, "Error sending email", mex);
        }
    }
}
