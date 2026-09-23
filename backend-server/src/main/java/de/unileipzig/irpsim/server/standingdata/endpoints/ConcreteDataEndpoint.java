package de.unileipzig.irpsim.server.standingdata.endpoints;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.unileipzig.irpsim.core.data.timeseries.LoadElement;
import de.unileipzig.irpsim.core.standingdata.DataLoader;
import de.unileipzig.irpsim.server.data.Responses;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Path("/stammdaten")
@Api(value = "/stammdaten/concretedata", tags = "Stammdaten", description = "Repräsentiert die konkreten Daten eines Datensatzes.")
public class ConcreteDataEndpoint {
	private static final Logger LOG = LogManager.getLogger(ConcreteDataEndpoint.class);

	public static final DateTimeFormatter DATEFORMAT = DateTimeFormat.forPattern("dd.MM.-HH:mm").withZone(DateTimeZone.UTC);

	@Path("/concretedata")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Gibt die Daten der angefragten Eingabe-Zeitreihen zurück", notes = "Es wird für die übergebenen Zeitreihen-Namen die Zeitreihenliste zurück gegeben.")
	@ApiResponses(value = {
			@ApiResponse(code = 400, message = "Invalid ID supplied"),
			@ApiResponse(code = 404, message = "Not Found") })
	public final Response getMultipleData(
			@ApiParam(name = "seriesid", value = "Definiert das Datum, ab dem Zeitreihendaten zurückgegeben werden sollen") @QueryParam("seriesid") final List<Integer> seriesids,
			@ApiParam(name = "start", value = "Definiert das Datum, ab dem Zeitreihendaten zurückgegeben werden sollen; nur in Kombination mit Enddatum und maxcount nutzbar.") @QueryParam("start") final String start,
			@ApiParam(name = "end", value = "Definiert das Datum, bis zu dem Zeitreihendaten zurückgegeben werden sollen; nur in Kombination mit Startdatum und maxcount nutzbar.") @QueryParam("end") final String end,
			@ApiParam(name = "maxcount", value = "Definiert die maximale Anzahl an Werten, die zurückgegeben werden soll; nur in Kombination mit Start- und Enddatum nutzbar.") @QueryParam("maxcount") final int maxcount,
			@Context final SecurityContext securityContext) {
		// Die Zeitreihen gehören zu Datensätzen und damit zu Stammdaten, deren
		// Leserecht hier geprüft wird.
		final Response refused = StandingDataAccess.checkDatensaetzeReadable(securityContext, seriesids);
		if (refused != null) {
			return refused;
		}

		try {
			final Map<Integer, List<LoadElement>> timeseriesData;
			final DataLoader loader;
			if (maxcount != 0) {
				if (start == null) {
					return Responses.badRequestResponse("Wenn ein maxcount angegeben ist, muss auch ein Startdatum angegeben werden.");
				}
				if (end == null) {
					return Responses.badRequestResponse("Wenn ein maxcount angegeben ist, muss auch ein Enddatum angegeben werden.");
				}

				final DateTime startDate = start != null ? DATEFORMAT.parseDateTime(start) : new DateTime(0);
				final DateTime endDate = end != null ? DATEFORMAT.parseDateTime(end) : new DateTime(Long.MAX_VALUE);
				loader = new DataLoader(seriesids, startDate, endDate, maxcount);

				if (loader.getMissingids().size() > 0) {
					return Responses.errorResponse("Zeitreihe " + loader.getMissingids() + " nicht definiert");
				}
			} else {
				loader = new DataLoader(seriesids);

			}
			timeseriesData = loader.getResultData();

			final ObjectMapper om = new ObjectMapper();

			final String jsonresult = om.writeValueAsString(timeseriesData);

			LOG.trace("Rückgabe: {}", jsonresult);

			return Response.ok(jsonresult).build();
		} catch (final Exception e) {
			LOG.error(e);
			e.printStackTrace();
			return Responses.errorResponse(e);
		}
	}
}
