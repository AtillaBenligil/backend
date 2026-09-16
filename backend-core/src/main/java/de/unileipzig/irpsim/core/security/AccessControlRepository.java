package de.unileipzig.irpsim.core.security;

import java.util.List;
import java.util.Set;

/**
 * Zugriff auf die gespeicherten Zugriffsrechte und Benutzergruppen.
 *
 * Die Schnittstelle trennt die Entscheidungslogik des
 * {@link AuthorizationService} von der Persistenz. Dadurch lässt sich die
 * Rechteprüfung in den Tests ohne Datenbank prüfen, während im Betrieb die
 * JPA-Implementierung verwendet wird.
 *
 * @author benligil
 */
public interface AccessControlRepository {

   /**
    * Liefert alle Zugriffsrechte an einer Ressource.
    *
    * @param resourceType Die Art der Ressource
    * @param resourceId Die Kennung der Ressource
    * @return Die Zugriffsrechte an der Ressource
    */
   List<AccessControlEntry> findEntries(ResourceType resourceType, long resourceId);

   /**
    * Liefert die Kennungen aller Ressourcen einer Art, für die überhaupt
    * Zugriffsrechte vergeben wurden.
    *
    * Ressourcen ohne Eintrag stammen aus der Zeit vor der Rechteverwaltung und
    * bleiben lesbar, damit bestehende Daten nicht unzugänglich werden.
    *
    * @param resourceType Die Art der Ressource
    * @return Die Kennungen der Ressourcen mit vergebenen Rechten
    */
   Set<Long> findRestrictedResources(ResourceType resourceType);

   /**
    * Liefert die Kennungen der Ressourcen, auf die die übergebenen Subjekte
    * Zugriff haben.
    *
    * @param resourceType Die Art der Ressource
    * @param username Der Anmeldename des Benutzers
    * @param groups Die Gruppen des Benutzers
    * @param required Das geforderte Recht
    * @return Die Kennungen der zugänglichen Ressourcen
    */
   Set<Long> findPermittedResources(ResourceType resourceType, String username, Set<String> groups, Permission required);

   /**
    * Liefert die Namen der Anwendungsgruppen, in denen der Benutzer Mitglied
    * ist.
    *
    * @param username Der Anmeldename des Benutzers
    * @return Die Namen der Gruppen des Benutzers
    */
   Set<String> findGroupNames(String username);

   /**
    * Speichert ein Zugriffsrecht.
    *
    * @param entry Das zu speichernde Zugriffsrecht
    */
   void save(AccessControlEntry entry);

   /**
    * Entfernt alle Zugriffsrechte an einer Ressource.
    *
    * Wird beim Löschen einer Ressource aufgerufen, damit keine verwaisten
    * Einträge zurückbleiben.
    *
    * @param resourceType Die Art der Ressource
    * @param resourceId Die Kennung der Ressource
    */
   void deleteEntries(ResourceType resourceType, long resourceId);
}
