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
 * Mitglieder der Administratorgruppe dürfen jede Ressource lesen und
 * verändern. Nur so lassen sich Ressourcen ohne Rechteeinträge überhaupt
 * löschen oder freigeben. Die Administratorrolle wird ausschließlich aus den
 * Gruppen des LDAP-Verzeichnisses abgeleitet und nicht aus den
 * Anwendungsgruppen, da sich Benutzer sonst über die Gruppenverwaltung selbst
 * zu Administratoren machen könnten.
 *
 * @author benligil
 */
public class AuthorizationService {

   private final AccessControlRepository repository;
   private final String administratorGroup;

   /**
    * Erzeugt den Dienst ohne Administratorrolle.
    *
    * @param repository Der Zugriff auf Rechte und Gruppen
    */
   public AuthorizationService(final AccessControlRepository repository) {
      this(repository, null);
   }

   /**
    * Erzeugt den Dienst mit der zu verwendenden Persistenz und Administratorgruppe.
    *
    * @param repository Der Zugriff auf Rechte und Gruppen
    * @param administratorGroup Der Name der Verzeichnisgruppe der Administratoren oder null
    */
   public AuthorizationService(final AccessControlRepository repository, final String administratorGroup) {
      this.repository = repository;
      this.administratorGroup = administratorGroup;
   }

   /**
    * Prüft, ob der Benutzer laut Verzeichnis Administrator ist.
    *
    * @param directoryGroups Die Gruppen des Benutzers aus dem LDAP-Verzeichnis
    * @return true, falls der Benutzer der Administratorgruppe angehört
    */
   public boolean isAdministrator(final Set<String> directoryGroups) {
      return administratorGroup != null && directoryGroups.contains(administratorGroup);
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
      if (isAdministrator(directoryGroups)) {
         return true;
      }
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
    * Liefert alle Rechte an einer Ressource.
    *
    * @param resourceType Die Art der Ressource
    * @param resourceId Die Kennung der Ressource
    * @return Die Rechteeinträge der Ressource
    */
   public List<AccessControlEntry> findEntries(final ResourceType resourceType, final long resourceId) {
      return repository.findEntries(resourceType, resourceId);
   }

   /**
    * Setzt das Recht eines Benutzers oder einer Gruppe an einer Ressource und
    * ersetzt dabei ein bereits vorhandenes Recht desselben Subjekts.
    *
    * @param resourceType Die Art der Ressource
    * @param resourceId Die Kennung der Ressource
    * @param subjectType Ob das Recht für einen Benutzer oder eine Gruppe gilt
    * @param subjectName Der Name des Benutzers oder der Gruppe
    * @param permission Das einzuräumende Recht
    * @throws IllegalStateException Falls damit das letzte Schreibrecht entfiele
    */
   public void setPermission(final ResourceType resourceType, final long resourceId, final SubjectType subjectType, final String subjectName,
         final Permission permission) {
      if (permission != Permission.WRITE) {
         requireOtherWriter(resourceType, resourceId, subjectType, subjectName);
      }
      repository.deleteEntry(resourceType, resourceId, subjectType, subjectName);
      repository.save(new AccessControlEntry(resourceType, resourceId, subjectType, subjectName, permission));
   }

   /**
    * Entzieht einem Benutzer oder einer Gruppe das Recht an einer Ressource.
    *
    * @param resourceType Die Art der Ressource
    * @param resourceId Die Kennung der Ressource
    * @param subjectType Ob das Recht für einen Benutzer oder eine Gruppe gilt
    * @param subjectName Der Name des Benutzers oder der Gruppe
    * @return true, falls ein Recht entzogen wurde
    * @throws IllegalStateException Falls damit das letzte Schreibrecht entfiele
    */
   public boolean revoke(final ResourceType resourceType, final long resourceId, final SubjectType subjectType, final String subjectName) {
      requireOtherWriter(resourceType, resourceId, subjectType, subjectName);
      return repository.deleteEntry(resourceType, resourceId, subjectType, subjectName);
   }

   /**
    * Stellt sicher, dass nach dem Entfernen oder Herabstufen eines Subjekts
    * noch ein anderes Subjekt das Schreibrecht besitzt.
    *
    * Ohne Schreibrecht hätte die Ressource entweder gar keine Einträge mehr und
    * wäre damit wie eine Altressource für alle lesbar, oder sie ließe sich nur
    * noch von Administratoren verwalten.
    *
    * @throws IllegalStateException Falls kein anderes Schreibrecht besteht
    */
   private void requireOtherWriter(final ResourceType resourceType, final long resourceId, final SubjectType subjectType,
         final String subjectName) {
      final List<AccessControlEntry> entries = repository.findEntries(resourceType, resourceId);
      final boolean affectsWriter = entries.stream().anyMatch(entry -> isSubject(entry, subjectType, subjectName)
            && entry.getPermission() == Permission.WRITE);
      final boolean otherWriter = entries.stream().anyMatch(entry -> !isSubject(entry, subjectType, subjectName)
            && entry.getPermission() == Permission.WRITE);
      if (affectsWriter && !otherWriter) {
         throw new IllegalStateException("Das letzte Schreibrecht an der Ressource kann nicht entzogen werden");
      }
   }

   private static boolean isSubject(final AccessControlEntry entry, final SubjectType subjectType, final String subjectName) {
      return entry.getSubjectType() == subjectType && entry.getSubjectName().equals(subjectName);
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
