package de.unileipzig.irpsim.server.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import de.unileipzig.irpsim.core.security.AccessControlEntry;
import de.unileipzig.irpsim.core.security.AccessControlRepository;
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

   private InMemoryRepository repository;
   private AuthorizationService service;

   /**
    * Legt vor jedem Test eine leere Rechteablage an.
    */
   @Before
   public void setUp() {
      repository = new InMemoryRepository();
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
    * Eine Rechteablage im Arbeitsspeicher für die Tests.
    */
   private static final class InMemoryRepository implements AccessControlRepository {

      private final List<AccessControlEntry> entries = new ArrayList<>();
      private final Map<String, Set<String>> memberships = new LinkedHashMap<>();

      private void addGroupMembership(final String username, final String group) {
         memberships.computeIfAbsent(username, key -> new LinkedHashSet<>()).add(group);
      }

      @Override
      public List<AccessControlEntry> findEntries(final ResourceType resourceType, final long resourceId) {
         return entries.stream()
               .filter(entry -> entry.getResourceType() == resourceType && entry.getResourceId() == resourceId)
               .collect(Collectors.toList());
      }

      @Override
      public Set<Long> findRestrictedResources(final ResourceType resourceType) {
         return entries.stream()
               .filter(entry -> entry.getResourceType() == resourceType)
               .map(AccessControlEntry::getResourceId)
               .collect(Collectors.toCollection(LinkedHashSet::new));
      }

      @Override
      public Set<Long> findPermittedResources(final ResourceType resourceType, final String username, final Set<String> groups,
            final Permission required) {
         return entries.stream()
               .filter(entry -> entry.getResourceType() == resourceType)
               .filter(entry -> entry.getPermission().includes(required))
               .filter(entry -> entry.getSubjectType() == SubjectType.USER
                     ? entry.getSubjectName().equals(username)
                     : groups.contains(entry.getSubjectName()))
               .map(AccessControlEntry::getResourceId)
               .collect(Collectors.toCollection(LinkedHashSet::new));
      }

      @Override
      public Set<String> findGroupNames(final String username) {
         return memberships.getOrDefault(username, Collections.emptySet());
      }

      @Override
      public void save(final AccessControlEntry entry) {
         entries.add(entry);
      }

      @Override
      public void deleteEntries(final ResourceType resourceType, final long resourceId) {
         entries.removeIf(entry -> entry.getResourceType() == resourceType && entry.getResourceId() == resourceId);
      }
   }
}
