package de.unileipzig.irpsim.core.security;

import java.util.List;
import java.util.Set;

/**
 * Entscheidet, ob ein Benutzer auf eine Ressource zugreifen darf.
 *
 * Die Prüfung wertet die gespeicherten Zugriffsrechte aus. Ein Benutzer erhält
 * Zugriff, wenn ihm das Recht unmittelbar oder über eine seiner Gruppen
 * eingeräumt wurde. Ressourcen, für die noch keine Rechte vergeben wurden,
 * bleiben lesbar, aber nicht veränderbar; dadurch bleiben die vor Einführung
 * der Rechteverwaltung angelegten Szenarien und Aufträge zugänglich, ohne dass
 * sie unbeabsichtigt bearbeitet werden können.
 *
 * @author benligil
 */
public class AuthorizationService {

   private final AccessControlRepository repository;

   /**
    * Erzeugt den Dienst mit der zu verwendenden Persistenz.
    *
    * @param repository Der Zugriff auf Rechte und Gruppen
    */
   public AuthorizationService(final AccessControlRepository repository) {
      this.repository = repository;
   }

   /**
    * Prüft, ob ein Benutzer das geforderte Recht an einer Ressource besitzt.
    *
    * @param username Der Anmeldename des Benutzers
    * @param directoryGroups Die Gruppen des Benutzers aus dem LDAP-Verzeichnis
    * @param resourceType Die Art der Ressource
    * @param resourceId Die Kennung der Ressource
    * @param required Das geforderte Recht
    * @return true, falls der Zugriff erlaubt ist
    */
   public boolean isPermitted(final String username, final Set<String> directoryGroups, final ResourceType resourceType, final long resourceId,
         final Permission required) {
      final List<AccessControlEntry> entries = repository.findEntries(resourceType, resourceId);
      if (entries.isEmpty()) {
         return required == Permission.READ;
      }

      final Set<String> groups = effectiveGroups(username, directoryGroups);
      for (final AccessControlEntry entry : entries) {
         if (!entry.getPermission().includes(required)) {
            continue;
         }
         if (entry.getSubjectType() == SubjectType.USER && entry.getSubjectName().equals(username)) {
            return true;
         }
         if (entry.getSubjectType() == SubjectType.GROUP && groups.contains(entry.getSubjectName())) {
            return true;
         }
      }
      return false;
   }

   /**
    * Liefert die Kennungen der Ressourcen, die für den Benutzer sichtbar sind.
    *
    * Enthalten sind sowohl die ausdrücklich freigegebenen Ressourcen als auch
    * die Ressourcen ohne vergebene Rechte.
    *
    * @param username Der Anmeldename des Benutzers
    * @param directoryGroups Die Gruppen des Benutzers aus dem LDAP-Verzeichnis
    * @param resourceType Die Art der Ressource
    * @param required Das geforderte Recht
    * @return Die Kennungen der zugänglichen Ressourcen
    */
   public Set<Long> findPermitted(final String username, final Set<String> directoryGroups, final ResourceType resourceType, final Permission required) {
      return repository.findPermittedResources(resourceType, username, effectiveGroups(username, directoryGroups), required);
   }

   /**
    * Liefert die Kennungen der Ressourcen, für die Rechte vergeben wurden.
    *
    * @param resourceType Die Art der Ressource
    * @return Die Kennungen der eingeschränkten Ressourcen
    */
   public Set<Long> findRestricted(final ResourceType resourceType) {
      return repository.findRestrictedResources(resourceType);
   }

   /**
    * Räumt einem Benutzer oder einer Gruppe ein Recht an einer Ressource ein.
    *
    * @param resourceType Die Art der Ressource
    * @param resourceId Die Kennung der Ressource
    * @param subjectType Ob das Recht für einen Benutzer oder eine Gruppe gilt
    * @param subjectName Der Name des Benutzers oder der Gruppe
    * @param permission Das einzuräumende Recht
    */
   public void grant(final ResourceType resourceType, final long resourceId, final SubjectType subjectType, final String subjectName,
         final Permission permission) {
      repository.save(new AccessControlEntry(resourceType, resourceId, subjectType, subjectName, permission));
   }

   /**
    * Entfernt alle Rechte an einer Ressource.
    *
    * @param resourceType Die Art der Ressource
    * @param resourceId Die Kennung der Ressource
    */
   public void revokeAll(final ResourceType resourceType, final long resourceId) {
      repository.deleteEntries(resourceType, resourceId);
   }

   /**
    * Vereinigt die Gruppen aus dem Verzeichnis mit den Anwendungsgruppen.
    *
    * @param username Der Anmeldename des Benutzers
    * @param directoryGroups Die Gruppen aus dem LDAP-Verzeichnis
    * @return Alle Gruppen des Benutzers
    */
   private Set<String> effectiveGroups(final String username, final Set<String> directoryGroups) {
      final Set<String> groups = new java.util.LinkedHashSet<>(directoryGroups);
      groups.addAll(repository.findGroupNames(username));
      return groups;
   }
}
