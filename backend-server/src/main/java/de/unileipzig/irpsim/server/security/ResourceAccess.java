package de.unileipzig.irpsim.server.security;

import java.util.Collections;
import java.util.Set;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import de.unileipzig.irpsim.core.security.AuthorizationService;
import de.unileipzig.irpsim.core.security.JpaAccessControlRepository;
import de.unileipzig.irpsim.core.security.Permission;
import de.unileipzig.irpsim.core.security.ResourceType;
import de.unileipzig.irpsim.core.security.SubjectType;

/**
 * Verbindet den Sicherheitskontext von JAX-RS mit der Rechteprüfung.
 *
 * Die Endpunkte greifen über diese Klasse auf die Rechteverwaltung zu und
 * müssen weder den angemeldeten Benutzer aus dem Kontext auslesen noch den
 * {@link AuthorizationService} selbst aufbauen. Dadurch bleibt die
 * Rechteprüfung in den Endpunkten auf eine Zeile beschränkt.
 *
 * @author benligil
 */
public final class ResourceAccess {

   private static AuthorizationService service = new AuthorizationService(new JpaAccessControlRepository());

   private ResourceAccess() {

   }

   /**
    * Ersetzt den verwendeten Dienst; wird von den Tests genutzt.
    *
    * @param newService Der zu verwendende Dienst
    */
   public static synchronized void setService(final AuthorizationService newService) {
      service = newService;
   }

   /**
    * Liefert den Dienst für die Rechteprüfung.
    *
    * @return Der Dienst für die Rechteprüfung
    */
   public static synchronized AuthorizationService getService() {
      return service;
   }

   /**
    * Liefert den Anmeldenamen des angemeldeten Benutzers.
    *
    * @param context Der Sicherheitskontext der Anfrage
    * @return Der Anmeldename oder null, falls kein Benutzer angemeldet ist
    */
   public static String username(final SecurityContext context) {
      final AuthenticatedUser user = user(context);
      return user != null ? user.getName() : null;
   }

   /**
    * Liefert die Verzeichnisgruppen des angemeldeten Benutzers.
    *
    * @param context Der Sicherheitskontext der Anfrage
    * @return Die Gruppen des Benutzers
    */
   public static Set<String> groups(final SecurityContext context) {
      final AuthenticatedUser user = user(context);
      return user != null ? user.getGroups() : Collections.emptySet();
   }

   /**
    * Prüft, ob der angemeldete Benutzer das geforderte Recht besitzt.
    *
    * @param context Der Sicherheitskontext der Anfrage
    * @param type Die Art der Ressource
    * @param id Die Kennung der Ressource
    * @param required Das geforderte Recht
    * @return true, falls der Zugriff erlaubt ist
    */
   public static boolean isPermitted(final SecurityContext context, final ResourceType type, final long id, final Permission required) {
      final AuthenticatedUser user = user(context);
      if (user == null) {
         return false;
      }
      return getService().isPermitted(user.getName(), user.getGroups(), type, id, required);
   }

   /**
    * Liefert die Kennungen der für den Benutzer sichtbaren Ressourcen.
    *
    * @param context Der Sicherheitskontext der Anfrage
    * @param type Die Art der Ressource
    * @return Die Kennungen der freigegebenen Ressourcen
    */
   public static Set<Long> permitted(final SecurityContext context, final ResourceType type) {
      final AuthenticatedUser user = user(context);
      if (user == null) {
         return Collections.emptySet();
      }
      return getService().findPermitted(user.getName(), user.getGroups(), type, Permission.READ);
   }

   /**
    * Liefert die Kennungen der Ressourcen, für die Rechte vergeben wurden.
    *
    * @param type Die Art der Ressource
    * @return Die Kennungen der eingeschränkten Ressourcen
    */
   public static Set<Long> restricted(final ResourceType type) {
      return getService().findRestricted(type);
   }

   /**
    * Räumt dem Anleger einer neuen Ressource das Schreibrecht ein.
    *
    * Ohne diesen Eintrag wäre eine neu angelegte Ressource für alle Benutzer
    * lesbar, aber für niemanden veränderbar.
    *
    * @param context Der Sicherheitskontext der Anfrage
    * @param type Die Art der Ressource
    * @param id Die Kennung der Ressource
    */
   public static void grantOwnership(final SecurityContext context, final ResourceType type, final long id) {
      final String username = username(context);
      if (username != null) {
         getService().grant(type, id, SubjectType.USER, username, Permission.WRITE);
      }
   }

   /**
    * Erzeugt die Antwort für einen abgelehnten Zugriff.
    *
    * @return Die Antwort mit Status 403
    */
   public static Response forbidden() {
      return Response.status(Response.Status.FORBIDDEN)
            .entity("{\"error\":\"Für diese Ressource fehlt die Berechtigung\"}")
            .type("application/json;charset=UTF-8")
            .build();
   }

   private static AuthenticatedUser user(final SecurityContext context) {
      if (context == null || context.getUserPrincipal() == null) {
         return null;
      }
      final java.security.Principal principal = context.getUserPrincipal();
      return principal instanceof AuthenticatedUser ? (AuthenticatedUser) principal : null;
   }
}
