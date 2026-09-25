package de.unileipzig.irpsim.server.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Priority;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.unileipzig.irpsim.core.security.Permission;
import de.unileipzig.irpsim.core.security.ResourceType;

/**
 * Prüft die Zugriffsrechte an einzelnen Ressourcen anhand des Pfads.
 *
 * Simulationsaufträge und Stammdaten werden jeweils über viele Endpunkte
 * angeboten, die alle demselben Pfadmuster {@code <art>/<id>/...} folgen. Statt
 * die Prüfung in jedem Endpunkt zu wiederholen, wertet dieser Filter die
 * Kennung aus dem Pfad aus und entscheidet anhand des HTTP-Verfahrens, ob ein
 * Lese- oder ein Schreibrecht erforderlich ist. Dadurch bleibt kein Endpunkt
 * versehentlich ungeschützt, wenn später weitere hinzukommen.
 *
 * Auflistungen werden nicht hier, sondern in der jeweiligen Abfrage
 * eingeschränkt, da dort die nicht sichtbaren Einträge bereits von der
 * Datenbank ausgeschlossen werden können. Endpunkte, die mehrere Ressourcen
 * über Anfrageparameter ansprechen, prüfen selbst.
 *
 * @author benligil
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public final class ResourceAuthorizationFilter implements ContainerRequestFilter {

   private static final Logger LOG = LogManager.getLogger(ResourceAuthorizationFilter.class);

   /** Die geschützten Pfadpräfixe und die Art der Ressource dahinter. */
   private static final Map<String, ResourceType> PROTECTED_PREFIXES = new LinkedHashMap<>();

   static {
      PROTECTED_PREFIXES.put("simulations/", ResourceType.JOB);
      PROTECTED_PREFIXES.put("stammdaten/", ResourceType.STAMMDATUM);
   }

   /**
    * Unterpfade, die trotz PUT nichts verändern: die Vorschau einer
    * algebraischen Berechnung wird nur berechnet, nicht gespeichert.
    */
   private static final String PREVIEW_SUFFIX = "/preview";

   @Override
   public void filter(final ContainerRequestContext requestContext) throws IOException {
      final String path = normalise(requestContext.getUriInfo().getPath());
      for (final Map.Entry<String, ResourceType> prefix : PROTECTED_PREFIXES.entrySet()) {
         if (path.startsWith(prefix.getKey())) {
            check(requestContext, path, prefix.getKey(), prefix.getValue());
            return;
         }
      }
   }

   private static void check(final ContainerRequestContext requestContext, final String path, final String prefix, final ResourceType type) {
      final Long id = readId(path, prefix);
      if (id == null) {
         return;
      }
      final Permission required = isReadOnly(requestContext.getMethod(), path) ? Permission.READ : Permission.WRITE;
      if (!ResourceAccess.isPermitted(requestContext.getSecurityContext(), type, id, required)) {
         LOG.info("Zugriff ({}) auf {} {} durch {} abgelehnt", required, type, id, ResourceAccess.username(requestContext.getSecurityContext()));
         requestContext.abortWith(ResourceAccess.forbidden());
      }
   }

   /**
    * Liest die Kennung der Ressource aus dem Pfad.
    *
    * @param path Der normalisierte Pfad der Anfrage
    * @param prefix Das Präfix der Ressourcenart
    * @return Die Kennung oder null, falls der Pfad keine Kennung enthält
    */
   private static Long readId(final String path, final String prefix) {
      final String remainder = path.substring(prefix.length());
      final int separator = remainder.indexOf('/');
      final String segment = separator < 0 ? remainder : remainder.substring(0, separator);
      try {
         return Long.valueOf(segment);
      } catch (final NumberFormatException e) {
         // Unterpfade ohne Kennung, etwa Auflistungen oder Importe, werden nicht hier geprüft.
         return null;
      }
   }

   private static boolean isReadOnly(final String method, final String path) {
      return HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method) || HttpMethod.OPTIONS.equals(method)
            || path.endsWith(PREVIEW_SUFFIX);
   }

   private static String normalise(final String path) {
      return path.startsWith("/") ? path.substring(1) : path;
   }
}
