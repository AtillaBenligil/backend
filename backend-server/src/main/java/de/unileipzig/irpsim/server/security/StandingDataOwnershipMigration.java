package de.unileipzig.irpsim.server.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.unileipzig.irpsim.core.security.AuthorizationService;
import de.unileipzig.irpsim.core.security.Permission;
import de.unileipzig.irpsim.core.security.ResourceType;
import de.unileipzig.irpsim.core.security.SubjectType;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManager;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManagerProxy;

/**
 * Übernimmt die früheren verantwortlichen Personen der Stammdaten in die
 * Rechteverwaltung.
 *
 * Die Personen waren nur über Name und E-Mail-Adresse erfasst, die Rechte
 * beziehen sich dagegen auf LDAP-Anmeldenamen. Jede Adresse wird daher über
 * das Dienstkonto im Verzeichnis gesucht; der gefundene Benutzer erhält das
 * Schreibrecht an dem Stammdatum. Adressen ohne eindeutigen Benutzer werden
 * protokolliert; das Stammdatum bleibt dann ohne Einträge und damit wie andere
 * Altdaten für alle lesbar und von Administratoren verwaltbar. Es geht also
 * keine Zuordnung verloren, und die ursprünglichen Personen bleiben in der
 * Datenbank erhalten.
 *
 * Die Übernahme läuft bei jedem Serverstart und ist wiederholbar: Stammdaten,
 * die bereits Rechteeinträge besitzen, werden übersprungen.
 *
 * @author benligil
 */
public final class StandingDataOwnershipMigration {

   private static final Logger LOG = LogManager.getLogger(StandingDataOwnershipMigration.class);

   /**
    * Sucht zu einer E-Mail-Adresse den Anmeldenamen im Verzeichnis.
    */
   @FunctionalInterface
   public interface MailLookup {

      /**
       * @param mail Die E-Mail-Adresse
       * @return Der Anmeldename, falls eindeutig gefunden
       * @throws AuthenticationException Falls das Verzeichnis nicht erreichbar ist
       */
      Optional<String> findUsernameByMail(String mail) throws AuthenticationException;
   }

   /**
    * Das Ergebnis einer Übernahme.
    */
   public static final class Result {
      private int migrated;
      private int skipped;
      private final Set<String> unmatchedMails = new LinkedHashSet<>();

      /** @return Die Anzahl der Stammdaten, für die Rechte eingetragen wurden */
      public int getMigrated() {
         return migrated;
      }

      /** @return Die Anzahl der Stammdaten, die bereits Rechte besaßen */
      public int getSkipped() {
         return skipped;
      }

      /** @return Die Adressen, zu denen kein eindeutiger Benutzer gefunden wurde */
      public Set<String> getUnmatchedMails() {
         return unmatchedMails;
      }
   }

   private final AuthorizationService service;
   private final MailLookup lookup;

   /**
    * @param service Die Rechteverwaltung, in die übernommen wird
    * @param lookup Die Suche der Anmeldenamen im Verzeichnis
    */
   public StandingDataOwnershipMigration(final AuthorizationService service, final MailLookup lookup) {
      this.service = service;
      this.lookup = lookup;
   }

   /**
    * Übernimmt die übergebenen Zuordnungen.
    *
    * Für jedes Stammdatum werden zuerst alle Adressen aufgelöst und erst danach
    * Rechte eingetragen. Fällt das Verzeichnis zwischendurch aus, bleibt das
    * Stammdatum dadurch vollständig unverändert und wird beim nächsten Start
    * erneut übernommen.
    *
    * @param assignments Die früheren Zuordnungen je Stammdatum
    * @return Das Ergebnis der Übernahme
    * @throws AuthenticationException Falls das Verzeichnis nicht erreichbar ist
    */
   public Result migrate(final Map<Integer, Set<String>> assignments) throws AuthenticationException {
      final Result result = new Result();
      for (final Map.Entry<Integer, Set<String>> assignment : assignments.entrySet()) {
         final int stammdatumId = assignment.getKey();
         if (!service.findEntries(ResourceType.STAMMDATUM, stammdatumId).isEmpty()) {
            result.skipped++;
            continue;
         }
         final Set<String> owners = new LinkedHashSet<>();
         for (final String mail : assignment.getValue()) {
            final Optional<String> username = lookup.findUsernameByMail(mail);
            if (username.isPresent()) {
               owners.add(username.get());
            } else {
               result.unmatchedMails.add(mail);
            }
         }
         for (final String owner : owners) {
            service.setPermission(ResourceType.STAMMDATUM, stammdatumId, SubjectType.USER, owner, Permission.WRITE);
         }
         if (!owners.isEmpty()) {
            result.migrated++;
            LOG.info("Stammdatum {}: Schreibrecht für {} übernommen", stammdatumId, owners);
         }
      }
      return result;
   }

   /**
    * Lädt die früheren Zuordnungen aus der Datenbank.
    *
    * @return Je Stammdatum die E-Mail-Adressen der früheren verantwortlichen Personen
    */
   public static Map<Integer, Set<String>> loadLegacyAssignments() {
      final Map<Integer, Set<String>> assignments = new LinkedHashMap<>();
      try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
         final List<Object[]> rows = em.createQuery("SELECT s.id, b.email, p.email FROM Stammdatum s "
               + "LEFT JOIN s.verantwortlicherBezugsjahr b LEFT JOIN s.verantwortlicherPrognosejahr p", Object[].class)
               .getResultList();
         for (final Object[] row : rows) {
            final Set<String> mails = new LinkedHashSet<>();
            addMail(mails, row[1]);
            addMail(mails, row[2]);
            if (!mails.isEmpty()) {
               assignments.put((Integer) row[0], mails);
            }
         }
      }
      return assignments;
   }

   private static void addMail(final Collection<String> mails, final Object mail) {
      if (mail != null && !mail.toString().trim().isEmpty()) {
         mails.add(mail.toString().trim());
      }
   }

   /**
    * Führt die Übernahme beim Serverstart aus.
    *
    * Fehler werden nur protokolliert und verhindern den Start nicht; die
    * Übernahme wird dann beim nächsten Start wiederholt.
    */
   public static void runAtStartup() {
      try {
         final Map<Integer, Set<String>> assignments = loadLegacyAssignments();
         if (assignments.isEmpty()) {
            LOG.info("Keine verantwortlichen Personen zu übernehmen");
            return;
         }
         final LdapAuthenticator directory = SecurityComponents.getAuthenticator();
         final Result result = new StandingDataOwnershipMigration(ResourceAccess.getService(), directory::findUsernameByMail)
               .migrate(assignments);
         LOG.info("Übernahme der verantwortlichen Personen: {} übernommen, {} bereits verwaltet, nicht zuzuordnen: {}",
               result.getMigrated(), result.getSkipped(), new ArrayList<>(result.getUnmatchedMails()));
      } catch (final AuthenticationException | RuntimeException e) {
         LOG.error("Übernahme der verantwortlichen Personen fehlgeschlagen; sie wird beim nächsten Start wiederholt", e);
      }
   }
}
