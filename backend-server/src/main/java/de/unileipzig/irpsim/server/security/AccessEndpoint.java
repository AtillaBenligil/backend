package de.unileipzig.irpsim.server.security;

import java.util.List;
import java.util.Locale;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import de.unileipzig.irpsim.core.data.simulationparameters.OptimisationScenario;
import de.unileipzig.irpsim.core.security.AccessControlEntry;
import de.unileipzig.irpsim.core.security.Permission;
import de.unileipzig.irpsim.core.security.ResourceType;
import de.unileipzig.irpsim.core.security.SubjectType;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManager;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManagerProxy;
import de.unileipzig.irpsim.core.simulation.data.persistence.OptimisationJobPersistent;
import de.unileipzig.irpsim.core.standingdata.data.Stammdatum;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * Endpunkt für die Freigabe von Ressourcen an Benutzer und Gruppen.
 *
 * Über den Endpunkt wird festgelegt, wer ein Szenario, einen
 * Simulationsauftrag oder ein Stammdatum lesen oder verändern darf. Die Freigabe selbst ist eine
 * Veränderung der Ressource und setzt daher das Schreibrecht an ihr voraus;
 * Administratoren besitzen es an allen Ressourcen.
 *
 * Der Endpunkt ist für alle Arten von Ressourcen gleich aufgebaut; die
 * Stammdaten wurden ohne neuen Endpunkt aufgenommen.
 *
 * @author benligil
 */
@Path("access")
@Api(value = "/access", tags = "Zugriffsrechte", description = "Freigabe von Szenarien, Simulationsaufträgen und Stammdaten an Benutzer und Gruppen")
public class AccessEndpoint {

   private static final Logger LOG = LogManager.getLogger(AccessEndpoint.class);

   /**
    * Liefert die Rechte an einer Ressource.
    *
    * @param type Die Art der Ressource, etwa SCENARIO oder JOB
    * @param id Die Kennung der Ressource
    * @param securityContext Der Sicherheitskontext der Anfrage
    * @return Die Rechteeinträge der Ressource
    */
   @GET
   @Path("/{type}/{id}")
   @Produces(MediaType.APPLICATION_JSON)
   @ApiOperation(value = "Liefert die Rechte an einer Ressource.", notes = "Nur für Benutzer mit Schreibrecht an der Ressource.")
   @ApiResponses(value = { @ApiResponse(code = 200, message = "Ok"), @ApiResponse(code = 403, message = "Kein Schreibrecht"),
         @ApiResponse(code = 404, message = "Ressource unbekannt") })
   public final Response getPermissions(@PathParam("type") final String type, @PathParam("id") final long id,
         @Context final SecurityContext securityContext) {
      final ResourceType resourceType = parseType(type);
      final Response refused = checkAccess(resourceType, id, securityContext);
      if (refused != null) {
         return refused;
      }
      final List<AccessControlEntry> entries = ResourceAccess.getService().findEntries(resourceType, id);
      return Response.ok(entries).build();
   }

   /**
    * Räumt einem Benutzer oder einer Gruppe ein Recht an einer Ressource ein
    * oder ändert ein bestehendes Recht.
    *
    * @param type Die Art der Ressource
    * @param id Die Kennung der Ressource
    * @param request Das einzuräumende Recht
    * @param securityContext Der Sicherheitskontext der Anfrage
    * @return Die Rechteeinträge der Ressource nach der Änderung
    */
   @PUT
   @Path("/{type}/{id}")
   @Consumes(MediaType.APPLICATION_JSON)
   @Produces(MediaType.APPLICATION_JSON)
   @ApiOperation(value = "Gibt eine Ressource für einen Benutzer oder eine Gruppe frei.",
         notes = "Setzt das Recht (READ oder WRITE) des Subjekts; ein bestehendes Recht desselben Subjekts wird ersetzt.")
   @ApiResponses(value = { @ApiResponse(code = 200, message = "Ok"), @ApiResponse(code = 400, message = "Unvollständige Angaben"),
         @ApiResponse(code = 403, message = "Kein Schreibrecht"), @ApiResponse(code = 404, message = "Ressource unbekannt"),
         @ApiResponse(code = 409, message = "Letztes Schreibrecht würde entfallen") })
   public final Response setPermission(@PathParam("type") final String type, @PathParam("id") final long id, final PermissionRequest request,
         @Context final SecurityContext securityContext) {
      final ResourceType resourceType = parseType(type);
      if (request == null || request.getSubjectType() == null || request.getPermission() == null
            || request.getSubjectName() == null || request.getSubjectName().trim().isEmpty()) {
         return error(Response.Status.BAD_REQUEST, "Subjektart, Subjektname und Recht müssen angegeben werden");
      }
      final Response refused = checkAccess(resourceType, id, securityContext);
      if (refused != null) {
         return refused;
      }
      try {
         ResourceAccess.getService().setPermission(resourceType, id, request.getSubjectType(), request.getSubjectName().trim(),
               request.getPermission());
      } catch (final IllegalStateException e) {
         return error(Response.Status.CONFLICT, e.getMessage());
      }
      LOG.info("Recht {} an {} {} für {} {} gesetzt", request.getPermission(), resourceType, id, request.getSubjectType(),
            request.getSubjectName());
      return Response.ok(ResourceAccess.getService().findEntries(resourceType, id)).build();
   }

   /**
    * Entzieht einem Benutzer oder einer Gruppe das Recht an einer Ressource.
    *
    * @param type Die Art der Ressource
    * @param id Die Kennung der Ressource
    * @param subjectType USER oder GROUP
    * @param subjectName Der Name des Benutzers oder der Gruppe
    * @param securityContext Der Sicherheitskontext der Anfrage
    * @return Eine leere Antwort mit Status 204
    */
   @DELETE
   @Path("/{type}/{id}/{subjectType}/{subjectName}")
   @Produces(MediaType.APPLICATION_JSON)
   @ApiOperation(value = "Entzieht einem Benutzer oder einer Gruppe das Recht an einer Ressource.",
         notes = "Das letzte Schreibrecht kann nicht entzogen werden, damit die Ressource nicht für alle sichtbar wird.")
   @ApiResponses(value = { @ApiResponse(code = 204, message = "Entzogen"), @ApiResponse(code = 403, message = "Kein Schreibrecht"),
         @ApiResponse(code = 404, message = "Ressource oder Recht unbekannt"),
         @ApiResponse(code = 409, message = "Letztes Schreibrecht würde entfallen") })
   public final Response revokePermission(@PathParam("type") final String type, @PathParam("id") final long id,
         @PathParam("subjectType") final String subjectType, @PathParam("subjectName") final String subjectName,
         @Context final SecurityContext securityContext) {
      final ResourceType resourceType = parseType(type);
      final SubjectType parsedSubjectType;
      try {
         parsedSubjectType = SubjectType.valueOf(subjectType.toUpperCase(Locale.ROOT));
      } catch (final IllegalArgumentException e) {
         return error(Response.Status.BAD_REQUEST, "Unbekannte Subjektart: " + subjectType);
      }
      final Response refused = checkAccess(resourceType, id, securityContext);
      if (refused != null) {
         return refused;
      }
      try {
         if (!ResourceAccess.getService().revoke(resourceType, id, parsedSubjectType, subjectName)) {
            return error(Response.Status.NOT_FOUND, "Für dieses Subjekt ist kein Recht eingetragen");
         }
      } catch (final IllegalStateException e) {
         return error(Response.Status.CONFLICT, e.getMessage());
      }
      LOG.info("Recht von {} {} an {} {} entzogen", parsedSubjectType, subjectName, resourceType, id);
      return Response.noContent().build();
   }

   /**
    * Prüft, ob die Ressource existiert und der Benutzer sie verwalten darf.
    *
    * @return null, falls der Zugriff erlaubt ist, sonst die Fehlerantwort
    */
   private static Response checkAccess(final ResourceType resourceType, final long id, final SecurityContext securityContext) {
      if (resourceType == null || !exists(resourceType, id)) {
         return error(Response.Status.NOT_FOUND, "Die Ressource ist unbekannt");
      }
      if (!ResourceAccess.isPermitted(securityContext, resourceType, id, Permission.WRITE)) {
         LOG.info("Rechteverwaltung an {} {} durch {} abgelehnt", resourceType, id, ResourceAccess.username(securityContext));
         return ResourceAccess.forbidden();
      }
      return null;
   }

   private static ResourceType parseType(final String type) {
      try {
         return ResourceType.valueOf(type.toUpperCase(Locale.ROOT));
      } catch (final IllegalArgumentException e) {
         return null;
      }
   }

   /**
    * Prüft, ob die Ressource existiert, damit keine Rechte an nicht
    * vorhandenen Kennungen vergeben werden.
    */
   private static boolean exists(final ResourceType resourceType, final long id) {
      try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
         switch (resourceType) {
         case SCENARIO:
            return em.find(OptimisationScenario.class, (int) id) != null;
         case JOB:
            return em.find(OptimisationJobPersistent.class, id) != null;
         case STAMMDATUM:
            return em.find(Stammdatum.class, (int) id) != null;
         default:
            return false;
         }
      }
   }

   private static Response error(final Response.Status status, final String message) {
      return Response.status(status).entity(new JSONObject().put("error", message).toString()).type(MediaType.APPLICATION_JSON).build();
   }
}
