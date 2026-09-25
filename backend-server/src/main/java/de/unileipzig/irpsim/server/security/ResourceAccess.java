package de.unileipzig.irpsim.server.security;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;

import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;

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

   /** Umgebungsvariable für den Namen der Administratorgruppe im Verzeichnis. */
   public static final String ENV_ADMIN_GROUP = "IRPSIM_ADMIN_GROUP";

   private static final String DEFAULT_ADMIN_GROUP = "irpsim-admins";

   private static AuthorizationService service = createDefaultService();

   private ResourceAccess() {

   }

   /**
    * Erzeugt den Dienst, wie er im Betrieb verwendet wird: mit der
    * Datenbankablage und der Administratorgruppe aus der Umgebung.
    *
    * @return Der Dienst für die Rechteprüfung im Betrieb
    */
   public static AuthorizationService createDefaultService() {
      return new AuthorizationService(new JpaAccessControlRepository(), administratorGroupFromEnvironment());
   }

   private static String administratorGroupFromEnvironment() {
      final String value = System.getenv(ENV_ADMIN_GROUP);
      return value != null && !value.isEmpty() ? value : DEFAULT_ADMIN_GROUP;
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
    * Prüft, ob der angemeldete Benutzer Administrator ist.
    *
    * @param context Der Sicherheitskontext der Anfrage
    * @return true, falls der Benutzer der Administratorgruppe des Verzeichnisses angehört
    */
   public static boolean isAdministrator(final SecurityContext context) {
      final AuthenticatedUser user = user(context);
      return user != null && getService().isAdministrator(user.getGroups());
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
    * Bildet die Abfragebedingung, die eine Auflistung auf die für den Benutzer
    * sichtbaren Ressourcen einschränkt.
    *
    * Die Einschränkung wird in die Abfrage aufgenommen, damit nicht sichtbare
    * Einträge bereits von der Datenbank ausgeschlossen werden. Administratoren
    * sehen alles; Ressourcen ohne Rechteeinträge stammen aus der Zeit vor der
    * Rechteverwaltung und bleiben für alle sichtbar; alle übrigen nur, wenn dem
    * Benutzer oder einer seiner Gruppen ein Recht eingeräumt wurde.
    *
    * @param builder Der Ersteller der Abfragebedingungen
    * @param id Der Ausdruck für die Kennung der Ressource in der Abfrage
    * @param type Die Art der Ressource
    * @param context Der Sicherheitskontext der Anfrage
    * @param toIdType Wandelt eine gespeicherte Kennung in den Typ der Kennungsspalte um
    * @return Die Bedingung für die sichtbaren Ressourcen
    */
   public static Predicate visibilityPredicate(final CriteriaBuilder builder, final Expression<?> id, final ResourceType type,
         final SecurityContext context, final Function<Long, ?> toIdType) {
      if (isAdministrator(context)) {
         return builder.conjunction();
      }
      final Set<Long> restricted = restricted(type);
      if (restricted.isEmpty()) {
         return builder.conjunction();
      }
      final Predicate withoutRights = builder.not(id.in(convert(restricted, toIdType)));

      final Set<Long> permitted = permitted(context, type);
      if (permitted.isEmpty()) {
         return withoutRights;
      }
      return builder.or(withoutRights, id.in(convert(permitted, toIdType)));
   }

   /**
    * Bildet die Sichtbarkeitsbedingung für Abfragen über die
    * Hibernate-Criteria-Schnittstelle, die etwa die Stammdatenverwaltung nutzt.
    *
    * Die Regeln entsprechen {@link #visibilityPredicate}.
    *
    * @param idProperty Der Name der Kennungseigenschaft der Entität
    * @param type Die Art der Ressource
    * @param context Der Sicherheitskontext der Anfrage
    * @param toIdType Wandelt eine gespeicherte Kennung in den Typ der Kennungsspalte um
    * @return Die Bedingung oder null, falls keine Einschränkung nötig ist
    */
   public static Criterion visibilityCriterion(final String idProperty, final ResourceType type, final SecurityContext context,
         final Function<Long, ?> toIdType) {
      if (isAdministrator(context)) {
         return null;
      }
      final Set<Long> restricted = restricted(type);
      if (restricted.isEmpty()) {
         return null;
      }
      final Criterion withoutRights = Restrictions.not(Restrictions.in(idProperty, convert(restricted, toIdType)));

      final Set<Long> permitted = permitted(context, type);
      if (permitted.isEmpty()) {
         return withoutRights;
      }
      return Restrictions.or(withoutRights, Restrictions.in(idProperty, convert(permitted, toIdType)));
   }

   /**
    * Liefert eine Prüfung der Sichtbarkeit für Ergebnisse, die nicht über eine
    * Datenbankabfrage eingeschränkt werden können.
    *
    * Die Rechte werden einmal geladen und anschließend für jede Kennung im
    * Arbeitsspeicher geprüft; die Regeln entsprechen {@link #visibilityPredicate}.
    *
    * @param context Der Sicherheitskontext der Anfrage
    * @param type Die Art der Ressource
    * @return Die Prüfung, ob eine Kennung sichtbar ist
    */
   public static java.util.function.Predicate<Long> visibilityFilter(final SecurityContext context, final ResourceType type) {
      if (isAdministrator(context)) {
         return id -> true;
      }
      final Set<Long> restricted = restricted(type);
      if (restricted.isEmpty()) {
         return id -> true;
      }
      final Set<Long> permitted = permitted(context, type);
      return id -> !restricted.contains(id) || permitted.contains(id);
   }

   private static List<?> convert(final Set<Long> ids, final Function<Long, ?> toIdType) {
      return ids.stream().map(toIdType).collect(Collectors.toList());
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
