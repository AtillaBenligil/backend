package de.unileipzig.irpsim.server.security;

import java.util.List;
import java.util.Optional;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
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

import de.unileipzig.irpsim.core.security.UserGroup;
import de.unileipzig.irpsim.core.security.UserGroupRepository;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * Endpunkt für die Verwaltung der Benutzergruppen.
 *
 * Über den Endpunkt werden Gruppen angelegt und gelöscht sowie Benutzer in
 * Gruppen aufgenommen und wieder entfernt. Die Gruppen bilden die Grundlage
 * dafür, Szenarien und Simulationsaufträge nicht nur einzelnen Benutzern,
 * sondern ganzen Arbeitsgruppen zugänglich zu machen.
 *
 * Die Auflistung steht allen angemeldeten Benutzern offen, damit sie Ressourcen
 * für eine Gruppe freigeben können. Anlegen, Löschen und das Ändern der
 * Mitglieder ist den Administratoren vorbehalten; sonst könnte sich jeder
 * Benutzer selbst in eine Gruppe aufnehmen und damit deren Rechte erlangen.
 *
 * @author benligil
 */
@Path("groups")
@Api(value = "/groups", tags = "Benutzergruppen", description = "Verwaltung der Benutzergruppen und ihrer Mitglieder")
public class GroupEndpoint {

   private static final Logger LOG = LogManager.getLogger(GroupEndpoint.class);

   private final UserGroupRepository repository = new UserGroupRepository();

   /**
    * Liefert alle angelegten Gruppen.
    *
    * @return Die Gruppen mit ihren Mitgliedern
    */
   @GET
   @Produces(MediaType.APPLICATION_JSON)
   @ApiOperation(value = "Liefert alle Benutzergruppen zurück.", notes = "Gibt die Gruppen einschließlich ihrer Mitglieder zurück.")
   @ApiResponses(value = { @ApiResponse(code = 200, message = "Ok") })
   public final Response getGroups() {
      final List<UserGroup> groups = repository.findAll();
      return Response.ok(groups).build();
   }

   /**
    * Legt eine Gruppe an.
    *
    * @param group Die anzulegende Gruppe
    * @return Die angelegte Gruppe
    */
   @PUT
   @Consumes(MediaType.APPLICATION_JSON)
   @Produces(MediaType.APPLICATION_JSON)
   @ApiOperation(value = "Legt eine Benutzergruppe an.", notes = "Erzeugt eine neue Gruppe; der Name muss eindeutig sein.")
   @ApiResponses(value = { @ApiResponse(code = 200, message = "Ok"), @ApiResponse(code = 409, message = "Gruppe existiert bereits"),
         @ApiResponse(code = 403, message = "Nur für Administratoren") })
   public final Response createGroup(final UserGroup group, @Context final SecurityContext securityContext) {
      if (!ResourceAccess.isAdministrator(securityContext)) {
         return forbidden(securityContext, "Gruppe anlegen");
      }
      if (group == null || group.getName() == null || group.getName().isEmpty()) {
         return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"Der Gruppenname darf nicht leer sein\"}").build();
      }
      if (repository.findByName(group.getName()).isPresent()) {
         return Response.status(Response.Status.CONFLICT).entity("{\"error\":\"Die Gruppe existiert bereits\"}").build();
      }
      final UserGroup saved = repository.save(group);
      LOG.info("Gruppe {} angelegt", saved.getName());
      return Response.ok(saved).build();
   }

   /**
    * Löscht eine Gruppe.
    *
    * @param name Der Name der Gruppe
    * @return Eine leere Antwort mit Status 204
    */
   @DELETE
   @Path("/{name}")
   @Produces(MediaType.APPLICATION_JSON)
   @ApiOperation(value = "Löscht eine Benutzergruppe.", notes = "Entfernt die Gruppe samt aller Mitgliedschaften.")
   @ApiResponses(value = { @ApiResponse(code = 204, message = "Gelöscht"), @ApiResponse(code = 404, message = "Gruppe unbekannt"),
         @ApiResponse(code = 403, message = "Nur für Administratoren") })
   public final Response deleteGroup(@PathParam("name") final String name, @Context final SecurityContext securityContext) {
      if (!ResourceAccess.isAdministrator(securityContext)) {
         return forbidden(securityContext, "Gruppe löschen");
      }
      if (!repository.deleteByName(name)) {
         return notFound();
      }
      LOG.info("Gruppe {} gelöscht", name);
      return Response.noContent().build();
   }

   /**
    * Nimmt einen Benutzer in eine Gruppe auf.
    *
    * @param name Der Name der Gruppe
    * @param username Der Anmeldename des Benutzers
    * @return Die geänderte Gruppe
    */
   @POST
   @Path("/{name}/members/{username}")
   @Produces(MediaType.APPLICATION_JSON)
   @ApiOperation(value = "Nimmt einen Benutzer in eine Gruppe auf.", notes = "Fügt den Benutzer der Mitgliederliste der Gruppe hinzu.")
   @ApiResponses(value = { @ApiResponse(code = 200, message = "Ok"), @ApiResponse(code = 404, message = "Gruppe unbekannt"),
         @ApiResponse(code = 403, message = "Nur für Administratoren") })
   public final Response addMember(@PathParam("name") final String name, @PathParam("username") final String username,
         @Context final SecurityContext securityContext) {
      if (!ResourceAccess.isAdministrator(securityContext)) {
         return forbidden(securityContext, "Mitglied aufnehmen");
      }
      final Optional<UserGroup> group = repository.findByName(name);
      if (!group.isPresent()) {
         return notFound();
      }
      group.get().addMember(username);
      final UserGroup saved = repository.save(group.get());
      LOG.info("Benutzer {} zur Gruppe {} hinzugefügt", username, name);
      return Response.ok(saved).build();
   }

   /**
    * Entfernt einen Benutzer aus einer Gruppe.
    *
    * @param name Der Name der Gruppe
    * @param username Der Anmeldename des Benutzers
    * @return Die geänderte Gruppe
    */
   @DELETE
   @Path("/{name}/members/{username}")
   @Produces(MediaType.APPLICATION_JSON)
   @ApiOperation(value = "Entfernt einen Benutzer aus einer Gruppe.", notes = "Nimmt den Benutzer aus der Mitgliederliste der Gruppe heraus.")
   @ApiResponses(value = { @ApiResponse(code = 200, message = "Ok"), @ApiResponse(code = 404, message = "Gruppe unbekannt"),
         @ApiResponse(code = 403, message = "Nur für Administratoren") })
   public final Response removeMember(@PathParam("name") final String name, @PathParam("username") final String username,
         @Context final SecurityContext securityContext) {
      if (!ResourceAccess.isAdministrator(securityContext)) {
         return forbidden(securityContext, "Mitglied entfernen");
      }
      final Optional<UserGroup> group = repository.findByName(name);
      if (!group.isPresent()) {
         return notFound();
      }
      group.get().removeMember(username);
      final UserGroup saved = repository.save(group.get());
      LOG.info("Benutzer {} aus Gruppe {} entfernt", username, name);
      return Response.ok(saved).build();
   }

   private static Response forbidden(final SecurityContext securityContext, final String action) {
      LOG.info("{} durch {} abgelehnt: keine Administratorrechte", action, ResourceAccess.username(securityContext));
      return ResourceAccess.forbidden();
   }

   private static Response notFound() {
      return Response.status(Response.Status.NOT_FOUND).entity("{\"error\":\"Die Gruppe ist unbekannt\"}").build();
   }
}
