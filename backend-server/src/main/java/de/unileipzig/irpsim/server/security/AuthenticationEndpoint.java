package de.unileipzig.irpsim.server.security;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * Endpunkt für die Anmeldung der Benutzer am LDAP-Verzeichnis.
 *
 * Der Endpunkt stellt die Anmeldung, die Abmeldung und die Passwortänderung
 * bereit. Die Prüfung der Zugangsdaten erfolgt im Verzeichnis; das Backend
 * vergibt nach erfolgreicher Anmeldung lediglich ein Zugriffstoken, das bei
 * den folgenden Anfragen im Authorization-Header mitgesendet wird.
 *
 * @author benligil
 */
@Path("auth")
@Api(value = "/auth", tags = "Authentifizierung", description = "Anmeldung, Abmeldung und Passwortänderung über das LDAP-Verzeichnis")
public class AuthenticationEndpoint {

   private static final Logger LOG = LogManager.getLogger(AuthenticationEndpoint.class);

   /**
    * Meldet einen Benutzer an und gibt das Zugriffstoken der Sitzung zurück.
    *
    * @param request Die Zugangsdaten des Benutzers
    * @return Das Zugriffstoken mit Benutzername und Gruppen
    */
   @POST
   @Path("login")
   @Consumes(MediaType.APPLICATION_JSON)
   @Produces(MediaType.APPLICATION_JSON)
   @ApiOperation(value = "Meldet einen Benutzer am LDAP-Verzeichnis an.",
         notes = "Prüft die übergebenen Zugangsdaten über einen Bind-Vorgang am Verzeichnis und gibt bei Erfolg ein Zugriffstoken zurück.")
   @ApiResponses(value = { @ApiResponse(code = 200, message = "Ok"), @ApiResponse(code = 401, message = "Zugangsdaten ungültig") })
   public final Response login(final LoginRequest request) {
      if (request == null) {
         return unauthorised();
      }
      try {
         final AuthenticatedUser user = SecurityComponents.getAuthenticator().authenticate(request.getUsername(), request.getPassword());
         final Session session = SecurityComponents.getSessionManager().create(user);

         final JSONObject result = new JSONObject();
         result.put("token", session.getToken());
         result.put("username", user.getName());
         result.put("groups", new JSONArray(user.getGroups()));
         return Response.ok(result.toString()).build();
      } catch (final AuthenticationException e) {
         LOG.info("Anmeldung abgelehnt: {}", e.getMessage());
         return unauthorised();
      }
   }

   /**
    * Beendet die Sitzung des angemeldeten Benutzers.
    *
    * @param securityContext Der Sicherheitskontext der Anfrage
    * @param authorization Der Authorization-Header mit dem Zugriffstoken
    * @return Eine leere Antwort mit Status 204
    */
   @POST
   @Path("logout")
   @Produces(MediaType.APPLICATION_JSON)
   @ApiOperation(value = "Meldet den Benutzer ab.", notes = "Beendet die Sitzung, das Zugriffstoken verliert damit seine Gültigkeit.")
   @ApiResponses(value = { @ApiResponse(code = 204, message = "Abgemeldet") })
   public final Response logout(@Context final SecurityContext securityContext,
         @javax.ws.rs.HeaderParam(AuthenticationFilter.AUTHORIZATION_HEADER) final String authorization) {
      final String token = authorization != null && authorization.startsWith(AuthenticationFilter.TOKEN_PREFIX)
            ? authorization.substring(AuthenticationFilter.TOKEN_PREFIX.length()).trim()
            : null;
      SecurityComponents.getSessionManager().invalidate(token);
      return Response.noContent().build();
   }

   /**
    * Ändert das Passwort des angemeldeten Benutzers im Verzeichnis.
    *
    * Nach erfolgreicher Änderung werden alle Sitzungen des Benutzers beendet,
    * damit eine erneute Anmeldung mit dem neuen Passwort erforderlich ist.
    *
    * @param securityContext Der Sicherheitskontext der Anfrage
    * @param request Das bisherige und das neue Passwort
    * @return Eine leere Antwort mit Status 204
    */
   @POST
   @Path("password")
   @Consumes(MediaType.APPLICATION_JSON)
   @Produces(MediaType.APPLICATION_JSON)
   @ApiOperation(value = "Ändert das Passwort des angemeldeten Benutzers.",
         notes = "Prüft das bisherige Passwort über einen Bind-Vorgang und schreibt anschließend das neue Passwort in das Verzeichnis.")
   @ApiResponses(value = { @ApiResponse(code = 204, message = "Passwort geändert"), @ApiResponse(code = 401, message = "Bisheriges Passwort ungültig") })
   public final Response changePassword(@Context final SecurityContext securityContext, final PasswordChangeRequest request) {
      final String username = securityContext.getUserPrincipal().getName();
      if (request == null) {
         return unauthorised();
      }
      try {
         SecurityComponents.getAuthenticator().changePassword(username, request.getOldPassword(), request.getNewPassword());
         SecurityComponents.getSessionManager().invalidateUser(username);
         return Response.noContent().build();
      } catch (final AuthenticationException e) {
         LOG.info("Passwortänderung für {} abgelehnt: {}", username, e.getMessage());
         return unauthorised();
      }
   }

   /**
    * Erzeugt eine Antwort für ungültige Zugangsdaten.
    *
    * Die Meldung unterscheidet bewusst nicht zwischen unbekanntem Benutzer und
    * falschem Passwort.
    *
    * @return Die Antwort mit Status 401
    */
   private static Response unauthorised() {
      return Response.status(Response.Status.UNAUTHORIZED)
            .entity(new JSONObject().put("error", "Zugangsdaten ungültig").toString())
            .build();
   }
}
