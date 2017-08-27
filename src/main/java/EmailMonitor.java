import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.event.MessageCountEvent;
import javax.mail.event.MessageCountListener;
import javax.mail.internet.MimeMultipart;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;

public class EmailMonitor {
	
	public static String host;
	public static String pwd;
	
	static {
		try {
			BufferedReader br = new BufferedReader(new FileReader("config.txt"));
			host = br.readLine();
			pwd = br.readLine();
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static String getTextFromMessage(Message message) throws MessagingException, IOException {
	    String result = "";
	    if (message.isMimeType("text/plain")) {
	        result = message.getContent().toString();
	    } else if (message.isMimeType("multipart/*")) {
	        MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
	        result = getTextFromMimeMultipart(mimeMultipart);
	    }
	    return result;
	}

	public static String getTextFromMimeMultipart(
	        MimeMultipart mimeMultipart)  throws MessagingException, IOException{
	    String result = "";
	    int count = mimeMultipart.getCount();
	    for (int i = 0; i < count; i++) {
	        BodyPart bodyPart = mimeMultipart.getBodyPart(i);
	        if (bodyPart.isMimeType("text/plain")) {
	            result = result + "\n" + bodyPart.getContent();
	            break; // without break same text appears twice in my tests
	        } else if (bodyPart.isMimeType("text/html")) {
	            String html = (String) bodyPart.getContent();
	            result = result + "\n" + org.jsoup.Jsoup.parse(html).text();
	        } else if (bodyPart.getContent() instanceof MimeMultipart){
	            result = result + getTextFromMimeMultipart((MimeMultipart)bodyPart.getContent());
	        }
	    }
	    return result;
	}

    public static void main(String[] args) {
        try {
            final String username = host;
            final String password = pwd;

            Properties properties = new Properties();
            properties.put("mail.imap.auth", "true");
            properties.put("mail.imap.host", "imap.gmail.com");
            properties.put("mail.imap.port", "993");

            Session session = Session.getDefaultInstance(properties, new Authenticator() {

                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            IMAPStore store = (IMAPStore) session.getStore("imaps");
            store.connect("imap.gmail.com", username, password);
            final IMAPFolder inbox = (IMAPFolder) store.getFolder("inbox");
            
            inbox.open(Folder.READ_ONLY);

            inbox.addMessageCountListener(new MessageCountListener() {

                @Override
                public void messagesRemoved(MessageCountEvent event) {

                }

                @Override
                public void messagesAdded(MessageCountEvent event) {
                    Message[] messages = event.getMessages();
                    String content = "";

                    for (Message message : messages) {
                        try {
                        	System.out.printf("Mail From:- %s\n", message.getFrom()[0].toString());
                        	if (message.isMimeType("text/plain")) {
                        		System.out.printf("Mail Subject:- %s\n", message.getContent());
                        	} else if (message.isMimeType("multipart/*")) {
                        		content = getTextFromMimeMultipart((MimeMultipart) message.getContent());
                        		System.out.printf("Mail Subject:- %s\n", message.getSubject());
                        		Quickstart.createEvent(content, message);
                        	}
                        } catch (MessagingException | IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });

            new Thread(new Runnable() {
                private static final long KEEP_ALIVE_FREQ = 10000;

                @Override
                public void run() {
                    while (!Thread.interrupted()) {
                        try {
                            inbox.idle();
                            Thread.sleep(KEEP_ALIVE_FREQ);                                  
                        } catch (InterruptedException e) {
                        } catch (MessagingException e) {
                        }
                    }
                }
            }).start();                 
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}