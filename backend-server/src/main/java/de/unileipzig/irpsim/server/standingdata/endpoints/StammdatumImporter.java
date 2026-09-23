package de.unileipzig.irpsim.server.standingdata.endpoints;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.unileipzig.irpsim.core.standingdata.data.Stammdatum;
import de.unileipzig.irpsim.server.data.Responses;
import de.unileipzig.irpsim.server.standingdata.transfer.ExistingStammdatum;
import de.unileipzig.irpsim.server.standingdata.transfer.TransferData;
import de.unileipzig.irpsim.server.standingdata.transfer.TransferDataImporter;

public class StammdatumImporter {

	final class ImporterTask implements Runnable {

		private final TransferData transferData;
		private final String owner;

		public ImporterTask(final TransferData transferData, final String owner) {
			this.transferData = transferData;
			this.owner = owner;
		}

		@Override
		public void run() {
			try {
				LOG.debug("Starte import");
				importSynchron(transferData, owner);
				LOG.debug("Import beendet");
			} catch (final Throwable e) {
				e.printStackTrace();
			}
			LOG.debug("Setze Instanz auf null");
			instance = null;
			LOG.debug("Instanz: {}", instance);

		}

	}

	private static final Logger LOG = LogManager.getLogger(StammdatumImporter.class);

	private static StammdatumImporter instance = null;

	private final Date start = new Date();
	private int current, size;
	// private final List<Response> lastStatus = new LinkedList<>();

	private static synchronized StammdatumImporter getInstance() {
		if (instance == null) {
			instance = new StammdatumImporter();
			return instance;
		} else {
			return null;
		}
	}

	public static boolean isActive() {
		return instance != null;
	}

	private StammdatumImporter() {
	}

	private Response importSynchron(final TransferData transferData, final String owner) throws JsonProcessingException {
		final TransferDataImporter importer = new TransferDataImporter(transferData, owner);
		final List<ExistingStammdatum> existingData = importer.importTransferData();
		if (transferData.getStammdaten().size() > 0) {
			size = transferData.getStammdaten().size();
			current = 0;
			for (final Stammdatum notImportedStammdatum : transferData.getStammdaten()) {
				final ExistingStammdatum ex = new ExistingStammdatum();
				ex.setExistingId(notImportedStammdatum.getId());
				ex.setImportId(0);
				ex.setNotImportedBecauseOfDependency(true);
				existingData.add(ex);
				current++;
			}
			final Map<String, Object> result = new HashMap<>();
			result.put("message",
					"Stammdatenimport wurde nicht vollständig ausgeführt, da Stammdaten mit gleichen Daten bereits vorhanden waren.");
			result.put("errors", existingData);
			return Response.status(Status.BAD_REQUEST).entity(new ObjectMapper().writeValueAsString(result)).build();
		}
		if (existingData.size() > 0) {
			final Map<String, Object> result = new HashMap<>();
			result.put("message",
					"Stammdatenimport wurde nicht vollständig ausgeführt, da Stammdaten mit gleichen Daten bereits vorhanden waren.");
			result.put("errors", existingData);
			return Response.status(Status.BAD_REQUEST).entity(new ObjectMapper().writeValueAsString(result)).build();
		} else {
			return Responses.okResponse("Import erfolgreich",
					"Alle " + importer.getImportCount() + " Stammdaten wurden erfolgreich importiert");
		}
	}

	private Response importData(final TransferData transferData, final String owner) {
		final Thread importerThread = new Thread(new ImporterTask(transferData, owner));
		importerThread.start();
		return Responses.okResponse("Import gestartet", "Import der Stammdaten erfolgreich angestoßen");
	}

	/**
	 * @param transferData Die zu importierenden Stammdaten
	 * @param owner Der Benutzer, der das Schreibrecht an den importierten Stammdaten erhält
	 */
	public static Response importPlainData(final TransferData transferData, final String owner) throws JsonProcessingException {
		final StammdatumImporter newInstance = getInstance();
		if (newInstance != null) {
			LOG.debug("Starte Plain-Import");
			final Response response = newInstance.importData(transferData, owner);
			return response;
		} else {
			String info = "Alter Import nicht abgeschlossen, Startdatum: "
					+ (instance != null ? instance.start + " (" + instance.current + " / " + instance.size + ")" : "");
			LOG.debug(info);
			return Responses.errorResponse(info, "Importfehler");
		}
	}

	public static Response importZIP(final InputStream fileInputStream, final String owner)
			throws IOException, JsonParseException, JsonMappingException, JsonProcessingException {
		final StammdatumImporter current = getInstance();
		if (current != null) {
			LOG.debug("Starte ZIP-Import");
			Response r = Responses.badRequestResponse("Upload konnte nicht geparst werden.");
			try (BufferedInputStream bis = new BufferedInputStream(fileInputStream);
					ZipInputStream zis = new ZipInputStream(bis)) {

				ZipEntry jarEntry;
				while ((jarEntry = zis.getNextEntry()) != null) {
					LOG.debug("Importiere {}", jarEntry.getSize());
					final String summarized = readZIP(zis);

					LOG.debug("Reading: " + summarized.substring(0, Math.min(100, summarized.length())));

					if (summarized.length() > 0) {
						final TransferData transferData = StammdatumEntityEndpoint.MAPPER.readValue(summarized,
								TransferData.class);
						r = current.importData(transferData, owner);
					}
					LOG.debug("Reading finished");
				}
			}
			return r;
		} else {
			LOG.debug("Alter Import nicht abgeschlossen");
			return Responses.errorResponse("Alter Import nicht abgeschlossen", "Importfehler");
		}
	}

	private static String readZIP(final ZipInputStream zis) {
		final Scanner scanner = new Scanner(zis); // Should NOT be closed here - else zis is closed
		final StringBuilder sb = new StringBuilder();
		while (scanner.hasNextLine()) {
			sb.append(scanner.nextLine());
		}
		final String summarized = sb.toString();
		scanner.close();
		return summarized;
	}

}
