package de.unileipzig.irpsim.server.security;

/**
 * Abstraktion des Verzeichnisdienstes, gegen den die Benutzer authentifiziert
 * werden.
 *
 * Die Schnittstelle trennt die Endpunkte und die Sitzungsverwaltung von der
 * konkreten LDAP-Anbindung. Dadurch kann in den Tests ein eingebetteter
 * Verzeichnisserver verwendet werden, ohne dass der Produktivcode verändert
 * werden muss.
 *
 * @author benligil
 */
public interface LdapAuthenticator {

   /**
    * Meldet einen Benutzer am Verzeichnis an.
    *
    * Die Prüfung erfolgt über einen Bind-Vorgang mit den übergebenen
    * Zugangsdaten; das Passwort wird vom Backend zu keinem Zeitpunkt
    * gespeichert.
    *
    * @param username Der Anmeldename des Benutzers
    * @param password Das Passwort des Benutzers
    * @return Der angemeldete Benutzer inklusive seiner Gruppenzugehörigkeiten
    * @throws AuthenticationException Falls die Zugangsdaten ungültig sind oder das Verzeichnis nicht erreichbar ist
    */
   AuthenticatedUser authenticate(String username, String password) throws AuthenticationException;

   /**
    * Ändert das Passwort eines Benutzers im Verzeichnis.
    *
    * Das bisherige Passwort wird zunächst über einen Bind-Vorgang geprüft,
    * damit eine bestehende Sitzung allein nicht ausreicht, um das Passwort zu
    * überschreiben.
    *
    * @param username Der Anmeldename des Benutzers
    * @param oldPassword Das bisherige Passwort
    * @param newPassword Das neue Passwort
    * @throws AuthenticationException Falls das bisherige Passwort falsch ist oder die Änderung abgelehnt wird
    */
   void changePassword(String username, String oldPassword, String newPassword) throws AuthenticationException;
}
