package de.unileipzig.irpsim.server.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Scanner;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.client.JerseyClient;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.unileipzig.irpsim.core.simulation.data.BackendParametersMultiModel;
import de.unileipzig.irpsim.core.simulation.data.json.IntermediarySimulationStatus;
import de.unileipzig.irpsim.core.simulation.data.json.JSONParametersMultimodel;
import de.unileipzig.irpsim.core.simulation.data.persistence.State;
import de.unileipzig.irpsim.core.standingdata.DataLoader;
import de.unileipzig.irpsim.server.ServerStarter;
import de.unileipzig.irpsim.server.optimisation.Job;
import de.unileipzig.irpsim.server.optimisation.queue.OptimisationJobHandler;
import de.unileipzig.irpsim.server.security.AuthenticatedUser;
import de.unileipzig.irpsim.server.security.SecurityComponents;

/**
 * Verwaltet das Starten- und Beenden des Testservers.
 *
 * @author reichelt
 */
public final class ServerTestUtils {

	private static final Logger LOG = LogManager.getLogger(ServerTestUtils.class);

	private static int port = 9005;
	public static String URI;
	public static String SZENARIEN_URI;
	public static String GDX_URI;
	public static String OPTIMISATION_URI;
	public static String TIMESERIES_URI;

	private HttpServer server;

	private static ServerTestUtils instance;

	private static String getURI() {
		return "http://localhost:" + port + "/" + ServerStarter.BASE_PATH + "/";
	}

	/**
	 * Liefert das gegebene Parameterset mit Metadata.
	 *
	 * @param source Die Quelldatei
	 * @return Lädt das Parameterset aus der Datei und ergänzt es um Metadaten zu einem Szenario
	 */
	public static String getParameterset(final File source) {
		final StringBuilder builder = new StringBuilder("{\"name\": \"BasisKonfig\", \"data\": ");
		try (Scanner scanner = new Scanner(source)) {
			final String parameters = scanner.useDelimiter("\\Z").next();
			builder.append(parameters);
		} catch (final FileNotFoundException e) {
			e.printStackTrace();
		}
		builder.append("}");
		return builder.toString();
	}

	/**
	 * Singleton Konstruktor.
	 */
	private ServerTestUtils() {
	}

	public static ServerTestUtils getInstance() {
		if (null == instance) {
			instance = new ServerTestUtils();
		}
		return instance;
	}

	/**
	 * Startet den Server.
	 */
   public void startServer() {

      port++;

      URI = getURI();
      SZENARIEN_URI = URI + "szenarien";
      GDX_URI = URI + "exportconfigurations";
      OPTIMISATION_URI = URI + "simulations";
      TIMESERIES_URI = URI + "timeseries";
      LOG.info("Starte Testserver, Port: {}, URI: {}", port, URI);
      DataLoader.initializeTimeseriesTables();
      final String baseURI = UriBuilder.fromUri("http://0.0.0.0").port(port).path(ServerStarter.BASE_PATH).toTemplate();

      try {
         server = ServerStarter.startServer(baseURI);
         openTestSession();
      } catch (ProcessingException processingException) {
         LOG.debug("Port {} ist bereits in Benutzung! Versuche Serverstart mit erhöhter Portnummer.", port);
         processingException.printStackTrace();
         startServer();
      }

   }


   /**
    * Legt eine Sitzung fuer die Integrationstests an.
    *
    * Der Testserver laeuft im selben Prozess, daher kann die Sitzung unmittelbar
    * ueber die Sitzungsverwaltung erzeugt werden. Das vermeidet, dass fuer jeden
    * bestehenden Integrationstest ein Verzeichnisserver bereitstehen muss; die
    * Anmeldung gegen das Verzeichnis wird in den Tests der Sicherheitsschicht
    * geprueft.
    */
   private static void openTestSession() {
      final AuthenticatedUser user = new AuthenticatedUser("testbenutzer",
            new HashSet<>(Arrays.asList("irpsim-admins")));
      RESTCaller.setToken(SecurityComponents.getSessionManager().create(user).getToken());
   }

	/**
	 * Testet ob ein Job korrekt beendet ist, d.h. ob sein Status die korrekten Schrittangaben enthält.
	 * 
	 * @param jobid
	 * @throws JsonParseException
	 * @throws JsonMappingException
	 * @throws IOException
	 */
	public static void isJobCorrectFinished(final long jobid) throws JsonParseException, JsonMappingException, IOException {
		final String testURI = ServerTestUtils.OPTIMISATION_URI + "/" + jobid + "/";
		LOG.info("URI: " + testURI);
		final Response response = RESTCaller.callGetResponse(testURI);
		final String resultstring = response.readEntity(String.class);
		LOG.info("GET: " + resultstring + " " + resultstring.getClass());

		final ObjectMapper om = new ObjectMapper();
		final IntermediarySimulationStatus iss = om.readValue(resultstring, IntermediarySimulationStatus.class);
		Assert.assertEquals(iss.getSimulationsteps(), iss.getFinishedsteps());
	}

	public static void testGDXEquality(final Response response, final Job job) throws IOException {
		byte[] serverGDXReturn;
		serverGDXReturn = IOUtils.toByteArray((InputStream) response.getEntity());
		LOG.debug("Simulationslauf beendet");

		byte[] realgdxresult = null;
		try (FileInputStream inputStream = new FileInputStream(job.getGDXResultFile(0, 0))) {
			realgdxresult = new byte[(int) job.getGDXResultFile(0, 0).length()];
			inputStream.read(realgdxresult);
		} catch (final FileNotFoundException e) {
			e.printStackTrace();
		} catch (final IOException e) {
			e.printStackTrace();
		}

		Assert.assertArrayEquals(realgdxresult, serverGDXReturn);
	}

	/**
	 * Stoppt den Server, löscht alle erzeugten Dateien.
	 */
	public void stopServer() {
//		DatabaseConnectionHandler.getInstance().closeConnections();
		OptimisationJobHandler.getInstance().killAllJobs();
		server.shutdownNow();
		// FileUtils.listFiles(new File("src/main/resources/gams/IRPsim/output/results"), new WildcardFileFilter("*.gdx"), TrueFileFilter.INSTANCE).stream().forEach(f -> f.delete());
		// FileUtils.listFiles(new File("src/main/resources/gams/IRPsim/output/results"), new WildcardFileFilter("*.csv"), TrueFileFilter.INSTANCE).stream().forEach(f -> f.delete());
		System.gc();
	}

	/**
	 * Stoppt den Server, löscht nichts.
	 */
	public void stopServerDontKillJobs() {
		ServerStarter.cleanup(server);
		try {
			Thread.sleep(2000);
		} catch (final InterruptedException e) {
			e.printStackTrace();
		}
//		DatabaseConnectionHandler.getInstance().closeConnections();
	}

	/**
	 * @param content Die zu berechnenden Parameter
	 * @param jc Ein {@link JerseyClient}
	 * @return Wenn onlystart true istwird das Ergebnis erst zurückgegeben, wenn alle Berechnungen usw. fertig sind, wenn false wird die ID des Simulationslaufes zurückgegeben.
	 */
	public static long startSimulation(final String content) {
		final Response response = RESTCaller.callPost(ServerTestUtils.OPTIMISATION_URI + "?type=Basismodell", content);
		final String loadstring = response.readEntity(String.class);
		LOG.debug("Antwort" + loadstring);
		final JSONArray jso = new JSONArray(loadstring);
		return jso.getLong(0);
	}

	/**
	 * @param id Der zu beendende Optimierungslauf
	 * @param jc Ein {@link JerseyClient}
	 */
	public static void stopJob(final long id) {
		final String testURI = ServerTestUtils.OPTIMISATION_URI + "/" + id + "?delete=false";
		LOG.info("Stop-URI: " + testURI);
		final Response response = RESTCaller.callDeleteResponse(testURI);
		LOG.info("Stop ausgeführt: " + response);
	}

	/**
	 * Wartet bis zum Ende der Optimierung, dabei wird überprüft, ob Standarts des Simulationsablaufs eingehalten werden.
	 *
	 * @param id ID des Simulationslaufs
	 * @return Gibt die gespeicherten Daten des Endergebnisses des Simulationslaufs zurück
	 */
	public static String waitForSimulationEnd(final long id) throws IOException, JsonParseException, JsonMappingException {
		final ObjectMapper om = new ObjectMapper();
		boolean running = true;
		String resultstring = null;
		final String testURI = OPTIMISATION_URI + "/" + id + "/";
		while (running) {
			LOG.debug("URI: " + testURI);
			resultstring = RESTCaller.callGet(testURI);
			LOG.trace("GET: " + resultstring + " " + resultstring.getClass());

			final IntermediarySimulationStatus iss = om.readValue(resultstring, IntermediarySimulationStatus.class);
			running = iss.isRunning() || iss.getState() == State.WAITING;

			LOG.debug("State: {}", iss.getState());
			LOG.debug("Running: {} FinishedSteps: {}", running, iss.getFinishedsteps());
			MatcherAssert.assertThat(iss.getFinishedsteps(), Matchers.lessThanOrEqualTo(iss.getSimulationsteps()));
			try {
				Thread.sleep(1000);
			} catch (final InterruptedException e1) {
				e1.printStackTrace();
			}
		}
		return resultstring;
	}

	@FunctionalInterface
	public interface TestForRun {
		public void check(long jobId, IntermediarySimulationStatus status) throws InterruptedException, IOException, JsonParseException, JsonMappingException;
	}

	public static String waitForSimulationEnd(final long jobid, final TestForRun test) throws IOException, JsonParseException, JsonMappingException, InterruptedException {
		final ObjectMapper om = new ObjectMapper();
		boolean running = true;
		String resultstring = null;
		final String testURI = OPTIMISATION_URI + "/" + jobid + "/";
		while (running) {
			LOG.trace("URI: " + testURI);
			resultstring = RESTCaller.callGet(testURI);
			LOG.trace("GET: " + resultstring + " " + resultstring.getClass());

			final IntermediarySimulationStatus state = om.readValue(resultstring, IntermediarySimulationStatus.class);
			running = state.isRunning() || state.getState() == State.WAITING;

			LOG.debug("Running: {} FinishedSteps: {}", running, state.getFinishedsteps());
			MatcherAssert.assertThat(state.getFinishedsteps(), Matchers.lessThanOrEqualTo(state.getSimulationsteps()));
			test.check(jobid, state);
			try {
				Thread.sleep(1000);
			} catch (final InterruptedException e1) {
				e1.printStackTrace();
			}
		}
		return resultstring;
	}

	/**
	 * @param jobId Die ID des Optimierungsjobs
	 * @return Das Teil- oder Endergebnis der Optimierung
	 */
	public static BackendParametersMultiModel fetchResults(final long jobId) throws IOException, JsonParseException, JsonMappingException {
		final String resultString = RESTCaller.callGet(OPTIMISATION_URI + "/" + jobId + "/results");
		final JSONParametersMultimodel resultGPJ = new ObjectMapper().readValue(resultString, JSONParametersMultimodel.class);
		final BackendParametersMultiModel result = new BackendParametersMultiModel(resultGPJ);
		return result;
	}

	/**
	 * Schickt das Parameterset an die Parameterschnittstelle.
	 *
	 * @param source Das Parameterset mit Zeitreihen, dass in die DB gespeichert werden soll
	 * @return gibt die ID des hinterlegten Parametersets zurück
	 */
	public static long putScenarioWithREST(final File source) throws FileNotFoundException {
		final String parameter = getParameterset(source);
		final Response response = RESTCaller.callPutResponse(SZENARIEN_URI, parameter);
		Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		final String entity = response.readEntity(String.class);
		final JSONObject jso = new JSONObject(entity);
		final long id = jso.getLong("id");
		return id;
	}

	/**
	 * Wartet bis zum Ende mehrerer Optimierungsjobs und versucht dieses Ende möglichst genau zu ermitteln.
	 *
	 * @param ids Liste der Ids der Optimierungsjobs
	 * @return Die Zeiten des Beendens der anderen Prozesse
	 */
	public List<Long> waitForPreciseOptimisationEnds(final List<Long> ids) throws JsonParseException, JsonMappingException, IOException {
		final List<Long> runningIds = new LinkedList<>(ids);
		final Long[] endTimes = new Long[ids.size()];
		final ObjectMapper om = new ObjectMapper();
		String resultstring = null;
		final String testURI = OPTIMISATION_URI + "/";

		while (!runningIds.isEmpty()) {
			State lastState = State.WAITING;
			final ListIterator<Long> idIt = runningIds.listIterator();
			while (idIt.hasNext()) {
				final Long id = idIt.next();
				resultstring = RESTCaller.callGet(testURI + id + "/");

				final IntermediarySimulationStatus iss = om.readValue(resultstring, IntermediarySimulationStatus.class);
				if (!iss.isRunning() && iss.getState() != State.WAITING) {
					endTimes[ids.indexOf(id)] = new Date().getTime();
					idIt.remove();
					continue;
				}
				if (iss.getState().compareTo(lastState) > 0) {
					lastState = iss.getState();
				}
			}
			if (runningIds.isEmpty()) {
				return new ArrayList<>(Arrays.asList(endTimes));
			}
			try {
				switch (lastState) {
				case PERSISTING:
					Thread.sleep(2000);
				case POSTPROCESSING:
					Thread.sleep(500);
				case ERROR:
				case FINISHED:
				case FINISHEDERROR:
					Thread.sleep(100);
					break;
				default:
					Thread.sleep(60000);
				}
			} catch (final InterruptedException e1) {
				e1.printStackTrace();
			}
		} // Methode sollte normalerweise eher das andere return aufrufen.
		LOG.warn("Methode endet an unerwarteter Stelle!");
		return new ArrayList<>(Arrays.asList(endTimes));
	}

	/**
	 * Wartet auf Beendigung aller aktiven oder wartenden Threads.
	 *
	 * @param maxTime Maximale Wartezeit
	 * @return Ob alle Jobs beendet wurden
	 */
	public static boolean waitForAllJobEnds(final long maxTime) {
		final long start = new Date().getTime();
		boolean empty = false;
		while (!empty && new Date().getTime() - start < maxTime) {
			empty = OptimisationJobHandler.getInstance().getRunningJobs().isEmpty() && OptimisationJobHandler.getInstance().getWaitingJobs().isEmpty();
			try {
				Thread.sleep(1000);
			} catch (final InterruptedException e) {
				e.printStackTrace();
			}
		}
		return empty;
	}
}
