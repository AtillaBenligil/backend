package de.unileipzig.irpsim.server;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;

import javax.ws.rs.core.UriBuilder;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.http.server.CLStaticHttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.StaticHttpHandler;
import org.glassfish.grizzly.servlet.WebappContext;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;

import de.unileipzig.irpsim.core.Constants;
import de.unileipzig.irpsim.core.data.timeseries.DatabaseConnectionHandler;
import de.unileipzig.irpsim.core.standingdata.DataLoader;
import de.unileipzig.irpsim.core.utils.PersistenceFolderUtil;
import de.unileipzig.irpsim.server.endpoints.Cleaner;
import de.unileipzig.irpsim.server.endpoints.ScenarioVersionUpdater;
import de.unileipzig.irpsim.server.optimisation.queue.OptimisationJobHandler;
import de.unileipzig.irpsim.server.security.AuthenticationFilter;
import de.unileipzig.irpsim.server.security.ResourceAuthorizationFilter;
import de.unileipzig.irpsim.server.security.SecurityComponents;
import de.unileipzig.irpsim.server.security.StandingDataOwnershipMigration;
import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.jaxrs.listing.ApiListingResource;
import io.swagger.jaxrs.listing.SwaggerSerializers;

/**
 * Verwaltet den Grizzly-HTTP-Server.
 *
 * @author reichelt
 */
public final class ServerStarter {
	
	public static final Logger LOG = LogManager.getLogger(ServerStarter.class);

	public static final String BASE_PATH = "simulation";

	/**
	 * private Konstruktor.
	 */
	private ServerStarter() {
	}

	/**
	 * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
	 *
	 * @param uri Die URI-Adresse des Servers
	 * @return Grizzly HTTP server
	 */
	public static HttpServer startServer(final String uri) {
		final BeanConfig beanConfig = new BeanConfig();
		beanConfig.setResourcePackage("de.unileipzig.irpsim.server");
		beanConfig.setBasePath(BASE_PATH);
		beanConfig.setDescription("Die Endpunkte zum Zugriff auf das IRPsim-Backend sind im folgenden dargestellt und nutzbar.");
		beanConfig.setTitle("IRPsim Endpunkte");
		beanConfig.setScan(true);

		final ResourceConfig rc = new ResourceConfig().packages("de.unileipzig.irpsim.server", "io.swagger.jersey.listing");
		rc.register(MultiPartFeature.class);
		rc.register(JacksonJaxbJsonProvider.class);
//		rc.register(JacksonJsonProvider.class);
		rc.register(JsonParseExceptionExceptionHandler.class);
		rc.register(ApiListingResource.class);
		rc.register(SwaggerSerializers.class);

		// Die Sicherheitskomponenten werden vor dem Registrieren des Filters
		// aufgebaut, damit die Verzeichnisanbindung beim ersten Zugriff steht.
		SecurityComponents.initialise();
		rc.register(AuthenticationFilter.class);
		rc.register(ResourceAuthorizationFilter.class);

		LOG.info("Starte Server unter URI: {}", uri);

		final HttpServer httpServer = GrizzlyHttpServerFactory.createHttpServer(URI.create(uri), rc);

		// Initialize and register Jersey Servlet
		final WebappContext context = new WebappContext("WebappContext", "");

		httpServer.getServerConfiguration().addHttpHandler(new StaticHttpHandler("swagger-ui"), "/swagger/");

		final CLStaticHttpHandler staticHttpHandler = new CLStaticHttpHandler(ServerStarter.class.getClassLoader(), "swagger-ui/");
		httpServer.getServerConfiguration().addHttpHandler(staticHttpHandler);

		context.deploy(httpServer);

		return httpServer;
	}

	/**
	 * Main method.
	 *
	 * @param args Die Programmstart Argumente
	 * @throws IOException Wird geworfen falls Lese- oder Schreibfehler auftauchen
	 */
	public static void main(final String[] args) throws IOException {
		Locale.setDefault(Locale.GERMANY);

		System.setProperty("com.mchange.v2.log.MLog", "com.mchange.v2.log.FallbackMLog");
		System.setProperty("com.mchange.v2.log.FallbackMLog.DEFAULT_CUTOFF_LEVEL", "WARNING");
		
		final Options options = new Options();

		final Option nameOption = Option.builder(Constants.DBURL).required(false).hasArg().desc("Gibt an, unter welcher der URL die Datenbank erreichbar ist").build();
		options.addOption(nameOption);

		final Option userOption = Option.builder(Constants.DBUSER).required(false).hasArg().desc("Gibt den Datenbankbenutzer für den Datenbankimport an").build();
		options.addOption(userOption);

		final Option passwordOption = Option.builder(Constants.DBPW).required(false).hasArg().desc("Gibt das Datenbankpasswort für den Datenbankimport an").build();
		options.addOption(passwordOption);

		final Option helpOption = Option.builder(Constants.HELP).required(false).desc("Zeigt die Hilfe an").build();
		options.addOption(helpOption);

		final Option dOption = Option.builder("d").required(false).desc("Startet den Server im Daemon-Modus").build();
		options.addOption(dOption);

		final Option portOption = Option.builder(Constants.IRPSIM_PORT).required(false).hasArg().desc("Legt den Serverport fest").build();
		options.addOption(portOption);

		final Option persistenceOption = Option.builder(Constants.IRPSIM_PERSISTENCEFOLDER).required(false).hasArg().desc("Legt den Pfad zum Persistieren von Daten fest").build();
		options.addOption(persistenceOption);

		final CommandLineParser parser = new DefaultParser();
		try {
		   CommandLine line = parser.parse(options, args);

			String dburl = System.getenv(Constants.IRPSIM_DATABASE_URL), user = System.getenv(Constants.IRPSIM_DATABASE_USER), pw = System.getenv(Constants.IRPSIM_DATABASE_PASSWORD);
			String port = "8282";
			if (line.hasOption(Constants.DBURL)) {
				dburl = line.getOptionValue(Constants.DBURL);
			}
			if (line.hasOption(Constants.DBUSER)) {
				user = line.getOptionValue(Constants.DBUSER);
			}
			if (line.hasOption(Constants.DBPW)) {
				pw = line.getOptionValue(Constants.DBPW);
			}
			if (System.getenv().containsKey(Constants.IRPSIM_PORT)) {
				port = System.getenv(Constants.IRPSIM_PORT);
			}

			File persistenceFolder = System.getenv(Constants.IRPSIM_PERSISTENCEFOLDER) != null ? new File(System.getenv(Constants.IRPSIM_PERSISTENCEFOLDER)) : null;
			if (line.hasOption(Constants.IRPSIM_PERSISTENCEFOLDER)) {
				LOG.debug("Persistenzordner: {}", line.getOptionValue(Constants.IRPSIM_PERSISTENCEFOLDER));
				persistenceFolder = new File(line.getOptionValue(Constants.IRPSIM_PERSISTENCEFOLDER));
			} else {
				final String getenv = System.getenv(Constants.IRPSIM_PERSISTENCEFOLDER);
				if (getenv != null) {
					persistenceFolder = new File(getenv);
				} else {
					LOG.error(Constants.IRPSIM_PERSISTENCEFOLDER + " sollte entweder als Startoption oder als Umgebungsvariable definiert sein!");
					persistenceFolder = new File("irpsim_persistence");
				}
			}
			if (!persistenceFolder.exists()) {
				LOG.error("Persistenz-Ordner existiert nicht!");
				persistenceFolder.mkdir();
			}
			if (!persistenceFolder.isDirectory()) {
				LOG.error("Persistenz-Ordner ist kein Ordner!");
				System.exit(1);
			}

			if (System.getenv(Constants.IRPSIM_PARALLEL_JOBS) != null && !System.getenv(Constants.IRPSIM_PARALLEL_JOBS).equals("")) {
				final int value = Integer.parseInt(System.getenv(Constants.IRPSIM_PARALLEL_JOBS));
				OptimisationJobHandler.setMaxParallelJobs(value);
			}

			PersistenceFolderUtil.setPersistenceFolder(persistenceFolder);

			final String baseURI = UriBuilder.fromUri("http://0.0.0.0").port(parsePort(port, 8282)).path(BASE_PATH).toTemplate();

			boolean fail = false;
			if (user == null) {
				LOG.error("Datenbankbenutzer nicht gesetzt.");
				fail = true;
			}
			if (dburl == null) {
				LOG.error("Datenbank-URL nicht gesetzt.");
				fail = true;
			}

			if (line.hasOption(Constants.HELP) || fail) {
				final HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("backend.jar", options);
				LOG.error("Weiterhin ist es möglich, mittels der Umgebungsvariablen " + Constants.IRPSIM_DATABASE_URL + ", "
						+ Constants.IRPSIM_DATABASE_PASSWORD + " und "
						+ Constants.IRPSIM_DATABASE_URL + " die Datenbankzugangsdaten zu setzen");
				System.exit(1);
			}

			DatabaseConnectionHandler.getInstance().setUser(user);
			DatabaseConnectionHandler.getInstance().setPassword(pw);
			DatabaseConnectionHandler.getInstance().setUrl(dburl);

			DataLoader.initializeTimeseriesTables();
			new StandardszenarioImporter().initializeScenarios();
			ScenarioVersionUpdater.update();
			// Die früheren verantwortlichen Personen der Stammdaten werden in die
			// Rechteverwaltung übernommen; bereits übernommene werden übersprungen.
			StandingDataOwnershipMigration.runAtStartup();
			new Cleaner().deleteNonReferencedData();

			synchronizeJobsFromDatabase();

			final HttpServer server = startServer(baseURI);

			if (!line.hasOption("d")) {
				System.out.println(String.format("Drücke Enter um Server zu beenden %1$s", baseURI));
				System.in.read();

				cleanup(server);
			} else {
				while (server.isStarted()) {
					try {
						Thread.sleep(5000);
					} catch (final InterruptedException e) {
						LOG.error(e);
					}
				}
			}
			installShutdownHook(server);
		} catch (final ParseException e) {
			LOG.error(e);
		}
	}

	/**
	 * Überprüft die Datenbank auf Jobs, die nicht beendet wurden.
	 */
	public static void synchronizeJobsFromDatabase() {
		OptimisationJobHandler.getInstance().restartJobs();
		OptimisationJobHandler.getInstance().handleUndefinedJobs();
	}

	/**
	 * Cleanup all resource we use, stop all simulations, stop the http server, close DB connections.
	 *
	 * @param server The server to be cleaned up
	 */
	public static void cleanup(final HttpServer server) {
		OptimisationJobHandler.getInstance().holdAllJobs();
		LOG.info("Shutting down.");
		server.shutdownNow();
	}

	/**
	 * Install hook for linux signals. Will be called if the OS sends SIGTERM, which is docker's way of telling us, that our container is going to be stopped.
	 *
	 * @param server The server to be hooked up
	 */
	private static void installShutdownHook(final HttpServer server) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> cleanup(server)));
	}

	/**
	 * Savely parse a text representation of an integer value. Returns default value if unparseable.
	 *
	 * @param port String to parse
	 * @param dflt default value to return if port is not parseable
	 * @return port number
	 */
	private static int parsePort(final String port, final int dflt) {
		try {
			return Integer.parseInt(port);
		} catch (final NumberFormatException e) {
			LOG.warn("Konnte Port {} nicht parsen, benutze Standard-Port {} stattdessen", port, dflt);
			return dflt;
		}
	}

}
