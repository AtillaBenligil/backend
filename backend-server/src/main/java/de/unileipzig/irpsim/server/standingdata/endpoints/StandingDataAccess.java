package de.unileipzig.irpsim.server.standingdata.endpoints;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.unileipzig.irpsim.core.security.ResourceType;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManager;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManagerProxy;
import de.unileipzig.irpsim.server.security.ResourceAccess;

/**
 * Prüft Lesezugriffe auf Stammdaten, die nicht über ihre Kennung im Pfad,
 * sondern über mehrere Kennungen in den Anfrageparametern angesprochen werden.
 *
 * Solche Anfragen erreicht der pfadbasierte Filter nicht. Datensätze und
 * Zeitreihen gehören jeweils zu einem Stammdatum; sie sind genau dann lesbar,
 * wenn ihr Stammdatum lesbar ist.
 *
 * @author benligil
 */
final class StandingDataAccess {

   private static final Logger LOG = LogManager.getLogger(StandingDataAccess.class);

   private StandingDataAccess() {

   }

   /**
    * Prüft, ob alle angegebenen Stammdaten lesbar sind.
    *
    * @param context Der Sicherheitskontext der Anfrage
    * @param stammdatumIds Die Kennungen der Stammdaten
    * @return null, falls alle lesbar sind, sonst die Antwort mit Status 403
    */
   static Response checkStammdatenReadable(final SecurityContext context, final Collection<Integer> stammdatumIds) {
      final Predicate<Long> visible = ResourceAccess.visibilityFilter(context, ResourceType.STAMMDATUM);
      for (final Integer id : stammdatumIds) {
         if (!visible.test(id.longValue())) {
            LOG.info("Lesezugriff auf Stammdatum {} durch {} abgelehnt", id, ResourceAccess.username(context));
            return ResourceAccess.forbidden();
         }
      }
      return null;
   }

   /**
    * Prüft, ob die Stammdaten aller angegebenen Datensätze lesbar sind.
    *
    * Datensätze ohne Stammdatum -- die Standarddatensätze -- gehören niemandem
    * und bleiben wie bisher lesbar.
    *
    * @param context Der Sicherheitskontext der Anfrage
    * @param datensatzIds Die Kennungen der Datensätze beziehungsweise Zeitreihen
    * @return null, falls alle lesbar sind, sonst die Antwort mit Status 403
    */
   static Response checkDatensaetzeReadable(final SecurityContext context, final Collection<Integer> datensatzIds) {
      if (datensatzIds == null || datensatzIds.isEmpty() || ResourceAccess.isAdministrator(context)) {
         return null;
      }
      final Set<Integer> stammdatumIds = new LinkedHashSet<>();
      try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
         final List<Integer> result = em.createQuery(
               "SELECT DISTINCT ds.stammdatum.id FROM Datensatz ds WHERE ds.id IN :ids AND ds.stammdatum IS NOT NULL", Integer.class)
               .setParameter("ids", datensatzIds)
               .getResultList();
         stammdatumIds.addAll(result);
      }
      return checkStammdatenReadable(context, stammdatumIds);
   }
}
