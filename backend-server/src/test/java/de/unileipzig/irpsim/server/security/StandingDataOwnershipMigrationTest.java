package de.unileipzig.irpsim.server.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import de.unileipzig.irpsim.core.security.AuthorizationService;
import de.unileipzig.irpsim.core.security.Permission;
import de.unileipzig.irpsim.core.security.ResourceType;
import de.unileipzig.irpsim.core.security.SubjectType;

/**
 * Prüft die Übernahme der früheren verantwortlichen Personen in die
 * Rechteverwaltung.
 *
 * Das Verzeichnis wird durch eine feste Zuordnung von E-Mail-Adressen zu
 * Anmeldenamen ersetzt; die Suche im echten Verzeichnis prüfen die
 * LDAP-Tests.
 *
 * @author benligil
 */
public class StandingDataOwnershipMigrationTest {

   private static final Set<String> NO_GROUPS = Collections.emptySet();

   private final Map<String, String> directory = new HashMap<>();
   private AuthorizationService service;
   private StandingDataOwnershipMigration migration;

   /**
    * Legt vor jedem Test eine leere Rechteablage und ein Verzeichnis mit zwei
    * Benutzern an.
    */
   @Before
   public void setUp() {
      directory.put("bob@irpsim.uni-leipzig.de", "bob");
      directory.put("carol@irpsim.uni-leipzig.de", "carol");
      service = new AuthorizationService(new InMemoryAccessControlRepository());
      migration = new StandingDataOwnershipMigration(service, mail -> Optional.ofNullable(directory.get(mail)));
   }

   /**
    * Beide verantwortlichen Personen müssen das Schreibrecht erhalten, andere
    * Benutzer keinen Zugriff.
    *
    * @throws Exception Falls die Übernahme unerwartet fehlschlägt
    */
   @Test
   public void testBothResponsiblePersonsBecomeWriters() throws Exception {
      final StandingDataOwnershipMigration.Result result = migration.migrate(assignment(1, "bob@irpsim.uni-leipzig.de", "carol@irpsim.uni-leipzig.de"));

      assertEquals(1, result.getMigrated());
      assertTrue(service.isPermitted("bob", NO_GROUPS, ResourceType.STAMMDATUM, 1L, Permission.WRITE));
      assertTrue(service.isPermitted("carol", NO_GROUPS, ResourceType.STAMMDATUM, 1L, Permission.WRITE));
      assertFalse(service.isPermitted("mallory", NO_GROUPS, ResourceType.STAMMDATUM, 1L, Permission.READ));
   }

   /**
    * Eine Adresse ohne Benutzer im Verzeichnis darf keine Rechte erzeugen und
    * muss gemeldet werden; das Stammdatum bleibt dann für alle lesbar.
    *
    * @throws Exception Falls die Übernahme unerwartet fehlschlägt
    */
   @Test
   public void testUnknownMailIsReportedAndDataStaysReadable() throws Exception {
      final StandingDataOwnershipMigration.Result result = migration.migrate(assignment(2, "unbekannt@test.de"));

      assertEquals(0, result.getMigrated());
      assertEquals(Collections.singleton("unbekannt@test.de"), result.getUnmatchedMails());
      assertTrue(service.findEntries(ResourceType.STAMMDATUM, 2L).isEmpty());
      assertTrue(service.isPermitted("mallory", NO_GROUPS, ResourceType.STAMMDATUM, 2L, Permission.READ));
   }

   /**
    * Ein erneuter Lauf darf bereits verwaltete Stammdaten nicht verändern;
    * sonst würden später entzogene Rechte bei jedem Serverstart zurückkehren.
    *
    * @throws Exception Falls die Übernahme unerwartet fehlschlägt
    */
   @Test
   public void testSecondRunSkipsManagedStandingData() throws Exception {
      migration.migrate(assignment(3, "bob@irpsim.uni-leipzig.de"));
      service.setPermission(ResourceType.STAMMDATUM, 3L, SubjectType.USER, "alice", Permission.WRITE);
      service.revoke(ResourceType.STAMMDATUM, 3L, SubjectType.USER, "bob");

      final StandingDataOwnershipMigration.Result second = migration.migrate(assignment(3, "bob@irpsim.uni-leipzig.de"));

      assertEquals(1, second.getSkipped());
      assertFalse(service.isPermitted("bob", NO_GROUPS, ResourceType.STAMMDATUM, 3L, Permission.READ));
   }

   /**
    * Fällt das Verzeichnis mitten in einem Stammdatum aus, darf dieses nicht
    * nur teilweise übernommen werden; es wird beim nächsten Start vollständig
    * übernommen.
    */
   @Test
   public void testDirectoryFailureLeavesStandingDataUntouched() {
      final StandingDataOwnershipMigration failing = new StandingDataOwnershipMigration(service, mail -> {
         if (mail.startsWith("carol")) {
            throw new AuthenticationException("Verzeichnis nicht erreichbar");
         }
         return Optional.ofNullable(directory.get(mail));
      });

      try {
         failing.migrate(assignment(4, "bob@irpsim.uni-leipzig.de", "carol@irpsim.uni-leipzig.de"));
         fail("Der Ausfall des Verzeichnisses muss gemeldet werden");
      } catch (final AuthenticationException e) {
         assertTrue(service.findEntries(ResourceType.STAMMDATUM, 4L).isEmpty());
      }
   }

   private static Map<Integer, Set<String>> assignment(final int stammdatumId, final String... mails) {
      final Map<Integer, Set<String>> assignments = new LinkedHashMap<>();
      assignments.put(stammdatumId, new LinkedHashSet<>(Arrays.asList(mails)));
      return assignments;
   }
}
