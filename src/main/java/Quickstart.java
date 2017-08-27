import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.Security;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.sun.mail.smtp.SMTPTransport;

public class Quickstart {
	/** Application name. */
	private static final String APPLICATION_NAME = "Google Calendar API Java Quickstart";

	/** Directory to store user credentials for this application. */
	private static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"),
			".credentials/calendar-java-quickstart");

	/** Global instance of the {@link FileDataStoreFactory}. */
	private static FileDataStoreFactory DATA_STORE_FACTORY;

	/** Global instance of the JSON factory. */
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

	/** Global instance of the HTTP transport. */
	private static HttpTransport HTTP_TRANSPORT;

	/**
	 * Global instance of the scopes required by this quickstart.
	 *
	 * If modifying these scopes, delete your previously saved credentials at
	 * ~/.credentials/calendar-java-quickstart
	 */
	private static final List<String> SCOPES = Arrays.asList(CalendarScopes.CALENDAR);

	public static com.google.api.services.calendar.Calendar service;

	static {
		try {
			HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
			service = getCalendarService();
		} catch (Throwable t) {
			t.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Creates an authorized Credential object.
	 * 
	 * @return an authorized Credential object.
	 * @throws IOException
	 */
	public static Credential authorize() throws IOException {
		// Load client secrets.
		InputStream in = Quickstart.class.getResourceAsStream("/client_secret.json");
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

		// Build flow and trigger user authorization request.
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
				clientSecrets, SCOPES).setDataStoreFactory(DATA_STORE_FACTORY).setAccessType("offline").build();
		Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
		System.out.println("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
		return credential;
	}

	/**
	 * Build and return an authorized Calendar client service.
	 * 
	 * @return an authorized Calendar client service
	 * @throws IOException
	 */
	public static com.google.api.services.calendar.Calendar getCalendarService() throws IOException {
		Credential credential = authorize();
		return new com.google.api.services.calendar.Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
				.setApplicationName(APPLICATION_NAME).build();
	}

	public static boolean checkIfIsValidDate(String startDate, String endDate) {
		DateTime start = new DateTime(startDate);
		DateTime end = new DateTime(endDate);
		Events events = null;
		try {
			events = service.events().list("primary").setMaxResults(1).setTimeMin(start).setTimeMax(end)
					.setOrderBy("startTime").setSingleEvents(true).execute();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.err.println("Deu pau!");
			e.printStackTrace();
		}
		if (events == null)
			return false;
		if (events.getItems().size() == 0)
			return true;
		return false;
	}

	public static void createEvent(String message, Message object) {
		System.err.println("Email recebido!");
		String[] fields = message.split("\n");
		for (int i = 0; i < fields.length; i++) {
			fields[i] = fields[i].trim();
			System.err.printf("%d: \"%s\"\n", i, fields[i]);
		}
		System.err.println("\n\n");

		String startDateTimeString = String.format("%sT%s-03:00", fields[1], fields[2]);
		DateTime startDateTime = new DateTime(startDateTimeString);

		org.joda.time.DateTime endDateTime = new org.joda.time.DateTime(startDateTimeString);

		endDateTime = endDateTime.plusMinutes(Integer.parseInt(fields[3].substring(0, 2)))
				.plusSeconds(Integer.parseInt(fields[3].substring(3, 5)));

		String regex = "\\.[0-9]{3}";
		String sdt = startDateTime.toString().replaceAll(regex, "");
		String edt = endDateTime.toString().replaceAll(regex, "");
		System.err.println("Start Time Object     : " + sdt);
		System.err.println("End Time Object       : " + edt);

		String content = String.format("Matéria: %s\nAluno: %s\nTrabalho: %s\nOrientador: %s\n", fields[4], fields[5],
				fields[6], fields[7]);

		boolean isValid = checkIfIsValidDate(sdt, edt);
		if (isValid) {
			Event ev = new Event().setSummary(fields[6]).setDescription(content)
					.setStart(new EventDateTime().setDateTime(new DateTime(sdt)).setTimeZone("America/Sao_Paulo"))
					.setEnd(new EventDateTime().setDateTime(new DateTime(edt)).setTimeZone("America/Sao_Paulo"));

			com.google.api.services.calendar.Calendar service;
			try {
				service = Quickstart.getCalendarService();
				ev = service.events().insert("primary", ev).execute();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		try {
			String remetente = object.getFrom()[0].toString();
			remetente = remetente.substring(remetente.indexOf('<') + 1, remetente.indexOf('>'));
			System.err.println(remetente);
			sendResponseMail(isValid, remetente);
		} catch (MessagingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void sendResponseMail(boolean valid, String to) {
		String subject = "Evento marcado com sucesso";
		String content = "Seu evento foi marcado com sucesso!\n";

		if (!valid) {
			subject = "Falha ao marcar evento";
			content = "Seu evento não pode ser marcado pois o horário especificado está ocupado!"
					+ "\nTente marcar com uma outra data ou outro horário\n";
		}

		try {
			Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
	        final String SSL_FACTORY = "javax.net.ssl.SSLSocketFactory";
	        // Get a Properties object
	        Properties props = System.getProperties();
	        props.setProperty("mail.smtps.host", "smtp.gmail.com");
	        props.setProperty("mail.smtp.socketFactory.class", SSL_FACTORY);
	        props.setProperty("mail.smtp.socketFactory.fallback", "false");
	        props.setProperty("mail.smtp.port", "465");
	        props.setProperty("mail.smtp.socketFactory.port", "465");
	        props.setProperty("mail.smtps.auth", "true");

	        props.put("mail.smtps.quitwait", "false");

	        Session sess = Session.getInstance(props, null);

	        // -- Create a new message --
	        final MimeMessage msg = new MimeMessage(sess);

	        // -- Set the FROM and TO fields --
	        msg.setFrom(new InternetAddress(EmailMonitor.host));
	        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));

	        msg.setSubject(subject);
	        msg.setText(content, "utf-8");
	        msg.setSentDate(new Date());

	        SMTPTransport t = (SMTPTransport)sess.getTransport("smtps");

	        t.connect("smtp.gmail.com", EmailMonitor.host, EmailMonitor.pwd);
	        t.sendMessage(msg, msg.getAllRecipients());      
	        t.close();
	        System.out.println("Resposta enviada!");
		} catch (MessagingException mex) {
			mex.printStackTrace();
		}
	}

	public static void main(String[] args) throws IOException, MessagingException {
		
	}

}

/*
 * std: 2017-08-28T14:00:00.000-03:00 edt: 2017-08-28T14:15:00.000-03:00
 */
