package de.unileipzig.irpsim.server.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import de.unileipzig.irpsim.core.security.AuthorizationService;
import de.unileipzig.irpsim.core.security.Permission;
import de.unileipzig.irpsim.core.security.ResourceType;
import de.unileipzig.irpsim.core.security.SubjectType;

/**
 * Prüft die Entscheidungen der Rechteverwaltung.
 *
 * Die Tests verwenden eine Ablage im Arbeitsspeicher, damit die
 * Entscheidungslogik unabhängig von der Datenbank geprüft werden kann.
 *
 * @author benligil
 */
public class AuthorizationServiceTest {

   private static final Set<String> NO_DIRECTORY_GROUPS = Collections.emptySet();

   private InMemoryAccessControlRepository repository;
   private AuthorizationService service;

   /**
    * Legt vor jedem Test eine leere Rechteablage an.
    */
   @Before
   public void setUp() {
      repository = new InMemoryAccessControlRepository();
      service = new AuthorizationService(repository);
   }

   /**
    * Ein unmittelbar eingeräumtes Recht muss den Zugriff erlauben.
    */
   @Test
   public void testDirectPermissionIsGranted() {
      service.grant(ResourceType.SCENARIO, 1L, SubjectType.USER, "bob", Permission.READ);

      assertTrue(service.isPermitted("bob", NO_DIRECTORY_GROUPS, ResourceType.SCENARIO, 1L, Permission.READ));
   }

   /**
    * Ein Benutzer ohne Eintrag darf eine eingeschränkte Ressource nicht sehen.
    */
   @Test
   public void testForeignUserIsDenied() {
      service.grant(ResourceType.SCENARIO, 1L, SubjectType.USER, "bob", Permission.READ);

      assertFalse(service.isPermitted("carol", NO_DIRECTORY_GROUPS, ResourceType.SCENARIO, 1L, Permission.READ));
   }

   /**
    * Ein Leserecht darf nicht zum Schreiben berechtigen.
    */
   @Test
   public void testReadPermissionDoesNotAllowWriting() {
      service.grant(ResourceType.SCENARIO, 1L, SubjectType.USER, "bob", Permission.READ);

      assertTrue(service.isPermitted("bob", NO_DIRECTORY_GROUPS, ResourceType.SCENARIO, 1L, Permission.READ));
      assertFalse(service.isPermitted("bob", NO_DIRECTORY_GROUPS, ResourceType.SCENARIO, 1L, Permission.WRITE));
   }

   /**
    * Ein Schreibrecht muss das Leserecht einschließen.
    */
   @Test
   public void testWritePermissionIncludesReading() {
      service.grant(ResourceType.SCENARIO, 1L, SubjectType.USER, "bob", Permission.WRITE);

      assertTrue(service.isPermitted("bob", NO_DIRECTORY_GROUPS, ResourceType.SCENARIO, 1L, Permission.READ));
      assertTrue(service.isPermitted("bob", NO_DIRECTORY_GROUPS, ResourceType.SCENARIO, 1L, Permission.WRITE));
   }

   /**
    * Ein Recht für eine Verzeichnisgruppe muss für alle Mitglieder gelten.
    */
   @Test
   public void testDirectoryGroupPermissionIsGranted() {
      service.grant(ResourceType.SCENARIO, 1L, SubjectType.GROUP, "irpsim-modellers", Permission.WRITE);

      final Set<String> groups = Collections.singleton("irpsim-modellers");
      assertTrue(service.isPermitted("bob", groups, ResourceType.SCENARIO, 1L, Permission.WRITE));
      assertFalse(service.isPermitted("carol", NO_DIRECTORY_GROUPS, ResourceType.SCENARIO, 1L, Permission.WRITE));
   }

   /**
    * Ein Recht für eine Anwendungsgruppe muss ebenfalls greifen.
    */
   @Test
   public void testApplicationGroupPermissionIsGranted() {
      repository.addGroupMembership("carol", "projekt-nord");
      service.grant(ResourceType.SCENARIO, 1L, SubjectType.GROUP, "projekt-nord", Permission.READ);

      assertTrue(service.isPermitted("carol", NO_DIRECTORY_GROUPS, ResourceType.SCENARIO, 1L, Permission.READ));
      assertFalse(service.isPermitted("bob", NO_DIRECTORY_GROUPS, ResourceType.SCENARIO, 1L, Permission.READ));
   }

   /**
    * Ein Benutzername darf nicht auf einen gleichnamigen Gruppeneintrag passen.
    */
   @Test
   public void testUserNameDoesNotMatchGroupEntry() {
      service.grant(ResourceType.SCENARIO, 1L, SubjectType.GROUP, "bob", Permission.READ);

      assertFalse(service.isPermitted("bob", NO_DIRECTORY_GROUPS, ResourceType.SCENARIO, 1L, Permission.READ));
   }

   /**
    * Ressourcen ohne vergebene Rechte bleiben lesbar, aber nicht veränderbar.
    */
   @Test
   public void testResourceWithoutEntriesStaysReadable() {
      assertTrue(service.isPermitted("bob", NO_DIRECTORY_GROUPS, ResourceType.SCENARIO, 99L, Permission.READ));
      assertFalse(service.isPermitted("bob", NO_DIRECTORY_GROUPS, ResourceType.SCENARIO, 99L, Permission.WRITE));
   }

   /**
    * Rechte an Szenarien dürfen nicht für Simulationsaufträge gelten.
    */
   @Test
   public void testPermissionsAreSeparatedByResourceType() {
      service.grant(ResourceType.SCENARIO, 1L, SubjectType.USER, "bob", Permission.WRITE);
      service.grant(ResourceType.JOB, 1L, SubjectType.USER, "carol", Permission.WRITE);

      assertFalse(service.isPermitted("bob", NO_DIRECTORY_GROUPS, ResourceType.JOB, 1L, Permission.WRITE));
      assertFalse(service.isPermitted("carol", NO_DIRECTORY_GROUPS, ResourceType.SCENARIO, 1L, Permission.WRITE));
   }

   /**
    * Die Auflistung muss genau die freigegebenen Ressourcen liefern.
    */
   @Test
   public void testPermittedResourcesAreListed() {
      service.grant(ResourceType.SCENARIO, 1L, SubjectType.USER, "bob", Permission.READ);
      service.grant(ResourceType.SCENARIO, 2L, SubjectType.GROUP, "irpsim-modellers", Permission.READ);
      service.grant(ResourceType.SCENARIO, 3L, SubjectType.USER, "carol", Permission.READ);

      final Set<Long> permitted = service.findPermitted("bob", Collections.singleton("irpsim-modellers"), ResourceType.SCENARIO, Permission.READ);

      assertEquals(new LinkedHashSet<>(Arrays.asList(1L, 2L)), permitted);
   }

   /**
    * Das Entfernen einer Ressource muss alle zugehörigen Rechte löschen.
    */
   @Test
   public void testRevokeRemovesAllEntries() {
      service.grant(ResourceType.SCENARIO, 1L, SubjectType.USER, "bob", Permission.WRITE);
      service.grant(ResourceType.SCENARIO, 1L, SubjectType.GROUP, "irpsim-modellers", Permission.READ);

      service.revokeAll(ResourceType.SCENARIO, 1L);

      assertTrue(service.findRestricted(ResourceType.SCENARIO).isEmpty());
   }

   /**
    * Administratoren dürfen auch fremde Ressourcen und Ressourcen ohne
    * Rechteeinträge verändern; sonst ließen sich diese nie löschen.
    */
   @Test
   public void testAdministratorMayWriteEverything() {
      final AuthorizationService withAdministrators = new AuthorizationService(repository, "irpsim-admins");
      withAdministrators.grant(ResourceType.JOB, 1L, SubjectType.USER, "bob", Permission.WRITE);
      final Set<String> adminGroups = Collections.singleton("irpsim-admins");

      assertTrue(withAdministrators.isAdministrator(adminGroups));
      assertTrue(withAdministrators.isPermitted("alice", adminGroups, ResourceType.JOB, 1L, Permission.WRITE));
      assertTrue(withAdministrators.isPermitted("alice", adminGroups, ResourceType.SCENARIO, 99L, Permission.WRITE));
      assertFalse(withAdministrators.isPermitted("carol", NO_DIRECTORY_GROUPS, ResourceType.JOB, 1L, Permission.WRITE));
   }

   /**
    * Eine gleichnamige Anwendungsgruppe darf keine Administratorrechte
    * verleihen, da sich Benutzer sonst über die Gruppenverwaltung selbst
    * befördern könnten.
    */
   @Test
   public void testApplicationGroupDoesNotGrantAdministratorRole() {
      final AuthorizationService withAdministrators = new AuthorizationService(repository, "irpsim-admins");
      repository.addGroupMembership("mallory", "irpsim-admins");

      assertFalse(withAdministrators.isPermitted("mallory", NO_DIRECTORY_GROUPS, ResourceType.SCENARIO, 99L, Permission.WRITE));
   }

   /**
    * Ohne konfigurierte Administratorgruppe gibt es keine Administratoren.
    */
   @Test
   public void testNoAdministratorsWithoutConfiguredGroup() {
      assertFalse(service.isAdministrator(Collections.singleton("irpsim-admins")));
      assertFalse(service.isPermitted("alice", Collections.singleton("irpsim-admins"), ResourceType.SCENARIO, 99L, Permission.WRITE));
   }

   /**
    * Das erneute Setzen eines Rechts muss den bisherigen Eintrag desselben
    * Subjekts ersetzen, statt einen zweiten anzulegen.
    */
   @Test
   public void testSetPermissionReplacesEntryOfSameSubject() {
      service.grant(ResourceType.SCENARIO, 1L, SubjectType.USER, "bob", Permission.WRITE);
      service.setPermission(ResourceType.SCENARIO, 1L, SubjectType.GROUP, "irpsim-viewers", Permission.READ);
      service.setPermission(ResourceType.SCENARIO, 1L, SubjectType.GROUP, "irpsim-viewers", Permission.WRITE);

      assertEquals(2, service.findEntries(ResourceType.SCENARIO, 1L).size());
      assertTrue(service.isPermitted("carol", Collections.singleton("irpsim-viewers"), ResourceType.SCENARIO, 1L, Permission.WRITE));
   }

   /**
    * Ein entzogenes Recht darf nicht mehr gelten.
    */
   @Test
   public void testRevokedPermissionNoLongerApplies() {
      service.grant(ResourceType.SCENARIO, 1L, SubjectType.USER, "bob", Permission.WRITE);
      service.setPermission(ResourceType.SCENARIO, 1L, SubjectType.USER, "carol", Permission.READ);

      assertTrue(service.revoke(ResourceType.SCENARIO, 1L, SubjectType.USER, "carol"));
      assertFalse(service.isPermitted("carol", NO_DIRECTORY_GROUPS, ResourceType.SCENARIO, 1L, Permission.READ));
   }

   /**
    * Das letzte Schreibrecht darf weder entzogen noch herabgestuft werden.
    * Ohne Einträge wäre die Ressource sonst wie eine Altressource für alle
    * lesbar.
    */
   @Test
   public void testLastWriterCannotBeRemoved() {
      service.grant(ResourceType.SCENARIO, 1L, SubjectType.USER, "bob", Permission.WRITE);

      try {
         service.revoke(ResourceType.SCENARIO, 1L, SubjectType.USER, "bob");
         org.junit.Assert.fail("Das letzte Schreibrecht darf nicht entzogen werden");
      } catch (final IllegalStateException e) {
         assertTrue(service.isPermitted("bob", NO_DIRECTORY_GROUPS, ResourceType.SCENARIO, 1L, Permission.WRITE));
      }
      try {
         service.setPermission(ResourceType.SCENARIO, 1L, SubjectType.USER, "bob", Permission.READ);
         org.junit.Assert.fail("Das letzte Schreibrecht darf nicht herabgestuft werden");
      } catch (final IllegalStateException e) {
         assertFalse(service.isPermitted("carol", NO_DIRECTORY_GROUPS, ResourceType.SCENARIO, 1L, Permission.READ));
      }

      service.setPermission(ResourceType.SCENARIO, 1L, SubjectType.GROUP, "irpsim-modellers", Permission.WRITE);
      assertTrue(service.revoke(ResourceType.SCENARIO, 1L, SubjectType.USER, "bob"));
   }
}
