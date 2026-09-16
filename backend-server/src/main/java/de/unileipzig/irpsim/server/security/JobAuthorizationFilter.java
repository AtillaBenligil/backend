package de.unileipzig.irpsim.server.security;

import java.io.IOException;

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
 * Prüft die Zugriffsrechte an einzelnen Simulationsaufträgen.
 *
 * Die Aufträge werden über zwölf Endpunkte angeboten, die alle demselben
 * Pfadmuster {@code simulations/&lt;id&gt;/...} folgen. Statt die Prüfung in
 * jedem Endpunkt einzeln zu wiederholen, wertet dieser Filter die Kennung aus
 * dem Pfad aus und entscheidet anhand des HTTP-Verfahrens, ob ein Lese- oder
 * ein Schreibrecht erforderlich ist. Dadurch bleibt kein Endpunkt
 * versehentlich ungeschützt, wenn später weitere hinzukommen.
 *
 * Die Auflistung der Aufträge wird nicht hier, sondern in der Abfrage selbst
 * eingeschränkt, da dort die nicht sichtbaren Einträge bereits von der
 * Datenbank ausgeschlossen werden können.
 *
 * @author benligil
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public final class JobAuthorizationFilter implements ContainerRequestFilter {

   private static final Logger LOG = LogManager.getLogger(JobAuthorizationFilter.class);

   private static final String JOB_PATH_PREFIX = "simulations/";

   @Override
   public void filter(final ContainerRequestContext requestContext) throws IOException {
      final String path = normalise(requestContext.getUriInfo().getPath());
      if (!path.startsWith(JOB_PATH_PREFIX)) {
         return;
      }

      final Long jobId = readJobId(path);
      if (jobId == null) {
         return;
      }

      final Permission required = isReadOnly(requestContext.getMethod()) ? Permission.READ : Permission.WRITE;
      if (!ResourceAccess.isPermitted(requestContext.getSecurityContext(), ResourceType.JOB, jobId, required)) {
         LOG.info("Zugriff auf Simulationsauftrag {} durch {} abgelehnt", jobId, ResourceAccess.username(requestContext.getSecurityContext()));
         requestContext.abortWith(ResourceAccess.forbidden());
      }
   }

   /**
    * Liest die Kennung des Auftrags aus dem Pfad.
    *
    * @param path Der normalisierte Pfad der Anfrage
    * @return Die Kennung oder null, falls der Pfad keine Kennung enthält
    */
   private static Long readJobId(final String path) {
      final String remainder = path.substring(JOB_PATH_PREFIX.length());
      final int separator = remainder.indexOf('/');
      final String segment = separator < 0 ? remainder : remainder.substring(0, separator);
      try {
         return Long.valueOf(segment);
      } catch (final NumberFormatException e) {
         // Unterpfade ohne Kennung, etwa die Auflistung, werden nicht hier geprüft.
         return null;
      }
   }

   private static boolean isReadOnly(final String method) {
      return HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method) || HttpMethod.OPTIONS.equals(method);
   }

   private static String normalise(final String path) {
      return path.startsWith("/") ? path.substring(1) : path;
   }
}
