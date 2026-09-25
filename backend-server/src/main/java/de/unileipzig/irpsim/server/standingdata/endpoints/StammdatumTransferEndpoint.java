package de.unileipzig.irpsim.server.standingdata.endpoints;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.unileipzig.irpsim.core.data.timeseries.LoadElement;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManager;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManagerProxy;
import de.unileipzig.irpsim.core.standingdata.DataLoader;
import de.unileipzig.irpsim.core.standingdata.TimeseriesValue;
import de.unileipzig.irpsim.core.standingdata.data.AlgebraicData;
import de.unileipzig.irpsim.core.standingdata.data.Stammdatum;
import de.unileipzig.irpsim.core.standingdata.data.StaticData;
import de.unileipzig.irpsim.server.data.Responses;
import de.unileipzig.irpsim.server.security.ResourceAccess;
import de.unileipzig.irpsim.server.standingdata.endpoints.data.ExportError;
import de.unileipzig.irpsim.server.standingdata.transfer.TransferData;
import de.unileipzig.irpsim.server.standingdata.transfer.TransferDatensatz;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Path("/stammdaten")
@Api(value = "/stammdaten/export", tags = "Stammdaten", description = "Verwaltet Im- und Export von Stammdaten.")
public class StammdatumTransferEndpoint {

	private static final Logger LOG = LogManager.getLogger(ExportError.class);

	@Path("/export")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Gibt die exportierbaren Stammdaten zurück.", notes = "")
	@ApiResponses(value = { @ApiResponse(code = 400, message = "Invalid ID supplied"),
			@ApiResponse(code = 404, message = "Not Found") })
	public final Response getStammdatumQueried(
			@ApiParam(name = "ids", value = "Definiert, welche Stammdaten exportiert werden sollen") @QueryParam("ids") final List<Integer> ids,
			@Context final SecurityContext securityContext) {
		try {
			if (ids.size() < 1) {
				return Responses.badRequestResponse("Mindestens eine ID muss übergeben werden!");
			}
			final Response refused = StandingDataAccess.checkStammdatenReadable(securityContext, ids);
			if (refused != null) {
				return refused;
			}

			final String resultString;
			try (final ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
				final TransferData data = new TransferData();
				final List<Stammdatum> stammdatumEntries = new LinkedList<>();
				for (final int id : ids) {
					final Stammdatum sd = em.find(Stammdatum.class, id);
					if (sd == null) {
						return Responses.errorResponse("Stammdatum " + id + " nicht vorhanden.");
					}
					stammdatumEntries.add(sd);
				}
				final List<ExportError> errors = new LinkedList<>();
				for (final Stammdatum sd : stammdatumEntries) {
					if (sd.getReferenz() != null && !ids.contains(sd.getReferenz().getId())) {
						final ExportError error = new ExportError();
						error.setId(sd.getId());
						error.setReferencedId(sd.getReferenz().getId());
						errors.add(error);
					}
				}
				if (errors.size() > 0) {
					// TODO Wieder zurückändern, wenn Frontend neue Datenstruktur unterstützt
					// final Map<String, Object> result = new HashMap<>();
					// result.put("message", "Stammdatenexport war nicht möglich, da einige
					// Stammdaten-Referenzen nicht exportiert wurden.");
					// result.put("errors", errors);
					// return Response.status(Status.BAD_REQUEST).entity(new
					// ObjectMapper().writeValueAsString(result)).build();
					String htmlString = "<table><tr><th>Stammdatum</th><th>Referenz</th></tr>";
					for (final ExportError error : errors) {
						htmlString += "<tr><td>" + error.getId() + "</td><td>" + error.getReferencedId() + "</td></tr>";
					}
					htmlString += "</table>";
					return Responses.badRequestResponse(
							"Stammdatenexport ist nicht möglich, da folgende Zuordnungen fehlerhaft waren: "
									+ htmlString);
				}

				final String idString = ids.toString().replace("[", "(").replace("]", ")");

				final Map<Integer, TransferDatensatz> datensaetze = new HashMap<>();
				for (final StaticData datensatz : ((List<StaticData>) em
						.createQuery("SELECT ds FROM StaticData ds WHERE ds.stammdatum IN " + idString)
						.getResultList())) {
					final TransferDatensatz transferDatensatz = new TransferDatensatz();
					transferDatensatz.setStammdatumId(datensatz.getStammdatum().getId());
					transferDatensatz.setJahr(datensatz.getJahr());
					transferDatensatz.setId(datensatz.getId());
					transferDatensatz.setSzenarioStelle(datensatz.getSzenario());
					LOG.debug("Speichere: {}", datensatz.getId());
					datensaetze.put(datensatz.getId(), transferDatensatz);
				}
				final List<Integer> datensatzIds = datensaetze.keySet().stream().collect(Collectors.toList());
				final DataLoader dataloader = new DataLoader(datensatzIds);
				for (final Entry<Integer, List<LoadElement>> timeseries : dataloader.getResultData().entrySet()) {
					final TransferDatensatz transferDatensatz = datensaetze.get(timeseries.getKey());
					for (final LoadElement loadElement : timeseries.getValue()) {
						final TimeseriesValue value = new TimeseriesValue();
						value.setUnixtimestamp(loadElement.getTime());
						value.setValue(loadElement.getAvg());
						transferDatensatz.getData().add(value);
					}
				}

				final Map<Stammdatum, AlgebraicData> algebraicData = new HashMap<>();
				for (final AlgebraicData datensatz : ((List<AlgebraicData>) em.createQuery("SELECT ds FROM "
						+ AlgebraicData.class.getSimpleName() + " ds WHERE ds.stammdatum IN " + idString)
						.getResultList())) {
					final AlgebraicData algebraic = algebraicData.get(datensatz.getStammdatum());
					if (algebraic == null) {
						algebraicData.put(datensatz.getStammdatum(), datensatz);
					} else {
						if (datensatz.getJahr() < algebraic.getJahr()) {
							algebraicData.put(datensatz.getStammdatum(), datensatz);
						}
					}
				}
				for (final AlgebraicData algebraic : algebraicData.values()) {
					data.getAlgebraicData().add(algebraic);
				}

				data.setStammdaten(stammdatumEntries);
				data.setDaten(new LinkedList<>(datensaetze.values()));
				resultString = new ObjectMapper().writeValueAsString(data);
			}

			return Response.ok(resultString).build();
		} catch (final Exception t) {
			t.printStackTrace();
			return Responses.errorResponse(t);
		}
	}

	@Path("/export")
	@PUT
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@ApiOperation(value = "Importiert die übergebenen Stammdaten", notes = "Der Import liefert eine Standardnachricht, wenn er erfolgreich war. Andernfalls wird ein Array von Objekten mit bereits existierenden Stammdaten"
			+ "zurückgegeben. Dabei wird jeweils die Id des Import-Stammdatums und des bereits existierenden Stammdatums angegeben.")
	@ApiResponses(value = { @ApiResponse(code = 400, message = "Invalid ID supplied"),
			@ApiResponse(code = 404, message = "Not Found") })
	public final Response addStammdaten(@FormDataParam("file") final InputStream fileInputStream,
			@FormDataParam("file") final FormDataContentDisposition contentDispositionHeader, @Context final SecurityContext securityContext) {
		final String owner = ResourceAccess.username(securityContext);
		try {

			if (contentDispositionHeader.getFileName().endsWith(".sds")
					|| contentDispositionHeader.getFileName().endsWith(".json")) {
				LOG.info("Reading JSON...");
				TransferData transferData = StammdatumEntityEndpoint.MAPPER.readValue(fileInputStream,
						TransferData.class);
				return StammdatumImporter.importPlainData(transferData, owner);
			} else if (contentDispositionHeader.getFileName().endsWith(".zip")) {
				LOG.info("Reading ZIP...");
				return StammdatumImporter.importZIP(fileInputStream, owner);
			} else {
				return Responses.okResponse("Typ unbekannt",
						"Typ " + contentDispositionHeader.getFileName() + " unbekannt");
			}

		} catch (final Exception t) {
			t.printStackTrace();
			return Responses.errorResponse(t);
		}
	}

	@Path("/importPossible")
	@GET
	public final boolean isExportPossible() {
		if (StammdatumImporter.isActive()) {
			return false;
		} else {
			return true;
		}
	}

}
