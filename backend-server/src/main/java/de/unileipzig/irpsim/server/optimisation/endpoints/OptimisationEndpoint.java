package de.unileipzig.irpsim.server.optimisation.endpoints;

import java.util.LinkedHashMap;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;

import de.unileipzig.irpsim.core.Constants;
import de.unileipzig.irpsim.core.security.Permission;
import de.unileipzig.irpsim.core.security.ResourceType;
import de.unileipzig.irpsim.core.simulation.data.json.JSONParameters;
import de.unileipzig.irpsim.core.simulation.data.json.JSONParametersMultimodel;
import de.unileipzig.irpsim.core.simulation.data.json.YearData;
import de.unileipzig.irpsim.server.data.Responses;
import de.unileipzig.irpsim.server.optimisation.queue.OptimisationJobHandler;
import de.unileipzig.irpsim.server.security.ResourceAccess;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * Endpunkt fürs Starten / Beenden einer Simulation.
 */
@Path("/simulations")
@Api(value = "/simulations", tags = "Simulation", description = "Verwaltet Simulationsläufe")
public class OptimisationEndpoint { // TODO Umbenennen JobstartEndpoint

   private static final int MAX_GAMS_IDENTIFIER_LENGTH = 61;

   private static final Logger LOG = LogManager.getLogger(OptimisationEndpoint.class);

   /**
    * Startet eine Optimierung durch den Aufruf der GAMS-API.
    *
    * @param model Modellname.
    * @param optimisationScenario Die Datengrundlage, für die optimiert werden soll.
    * @return Gibt den Status 200 (OK) mit der Id des Jobs zurück wenn die Optimierung erfolgreich gestartet ist, sonst eine Fehlermeldung.
    */
   @POST
   @Produces(MediaType.APPLICATION_JSON)
   @Consumes(MediaType.APPLICATION_JSON)
   @ApiOperation(value = "Startet die Simulation", notes = "Startet eine Simulation durch den Aufruf der GAMS-API. ")
   @ApiResponses(value = { @ApiResponse(code = 200, message = "Ok"), @ApiResponse(code = 400, message = "Bad Request") })
   public final Response startSimulation(final String optimisationScenario, @Context final SecurityContext securityContext) {
      LOG.debug("Starte Simulation: " + optimisationScenario.substring(0, 500));
      try {
         final JSONParametersMultimodel gamsparameters = Constants.MAPPER.readValue(optimisationScenario, JSONParametersMultimodel.class);
         final String wrongSets = findWrongSets(gamsparameters);
         if (wrongSets.length() > 0) {
            return Responses.badRequestResponse("Deklarierte Namen sind nicht zulässig: " + wrongSets + ". Namen dürfen maximal 61 Zeichen lang sein.");
         }
         long jobid = OptimisationJobHandler.getInstance().newJob(gamsparameters);
         // Ohne diesen Eintrag waere der Auftrag fuer alle lesbar, aber fuer
         // niemanden ausser den Administratoren abbrechbar oder loeschbar. Bis
         // der Eintrag gespeichert ist, ist der Auftrag hoechstens lesbar.
         ResourceAccess.grantOwnership(securityContext, ResourceType.JOB, jobid);

         return Response.status(Response.Status.OK).entity("[" + jobid + "]").build();
      } catch (final Exception e) {
         e.printStackTrace();
         return Responses.errorResponse(e, "Starte Simulation");
      }
   }

   /**
    * @param gp Die Parameter
    * @return {@link String}, in dem alle Setnamen mit Komma getrennt aufgelistet sind, deren Name länger als von GAMS erlaubt ist
    */
   public final String findWrongSets(final JSONParametersMultimodel gp) {
      String wrongSets = "";
      for (JSONParameters model : gp.getModels()) {
         for (final YearData year : model.getYears()) {
            if (year != null) {
               for (final LinkedHashMap<String, LinkedHashMap<String, Object>> entry : year.getSets().values()) {
                  for (final String setname : entry.keySet()) {
                     if (setname.length() > MAX_GAMS_IDENTIFIER_LENGTH) {
                        wrongSets += setname + ", ";
                     }
                  }
               }
            }
         }
      }
      return wrongSets;
   }

   /**
    * Gibt die zuvor gespeicherte Liste aller Simulationsläufe zurück. Es werden auch beendete Simulationsläufe ausgegeben.
    *
    * 
    *
    * @param running Gibt an ob gerade laufende Simulationsjob oder wartende zurückgegeben werden sollen.
    * @return Die Liste der laufenden Simulationsjobs oder eine Fehlermeldung.
    */
   @GET
   @Produces(MediaType.APPLICATION_JSON)
   @ApiOperation(value = "Gibt alle Simulationsjobs zurück.", notes = "Gibt die zuvor gespeicherte Liste aller Simulationsläufe zurück. Es werden auch beendete Simulationsläufe ausgegeben.")
   @ApiResponses(value = { @ApiResponse(code = 200, message = "Ok") })
   @Deprecated // wird scheinbar nicht mehr genutzt
   public final Response getSimulationList(@DefaultValue("false") @QueryParam("running") final boolean running,
         @Context final SecurityContext securityContext) {
      try {
         if (running) {
            final JSONArray jsa = new JSONArray();
            OptimisationJobHandler.getInstance().getRunningJobs().stream()
                  .filter(job -> ResourceAccess.isPermitted(securityContext, ResourceType.JOB, job.getId(), Permission.READ))
                  .forEach(job -> jsa.put(job.getId()));
            LOG.info("Laufende Optimierungen, IDs: {}", jsa);
            return Response.status(Response.Status.OK).entity(jsa.toString()).build();
         } else {
            final JSONArray jsa = new JSONArray();
            OptimisationJobHandler.getInstance().getPersistedJobs().stream()
                  .filter(job -> ResourceAccess.isPermitted(securityContext, ResourceType.JOB, job.getId(), Permission.READ))
                  .forEachOrdered(job -> jsa.put(job.getId()));
            LOG.info("Gespeicherte Optimierungen, IDs: {}", jsa);
            return Response.status(Response.Status.OK).entity(jsa.toString()).build();
         }
      } catch (final Exception e) {
         e.printStackTrace();
         return Responses.errorResponse(e, "Simulationsstatus");
      }
   }
}
