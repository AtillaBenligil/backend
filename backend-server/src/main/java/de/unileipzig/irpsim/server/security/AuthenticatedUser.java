package de.unileipzig.irpsim.server.security;

import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Der angemeldete Benutzer einer Sitzung.
 *
 * Die Instanz wird nach erfolgreicher Anmeldung am LDAP-Verzeichnis erzeugt und
 * hält neben dem Anmeldenamen die Gruppenzugehörigkeiten, die für die
 * Rechteprüfung ausgewertet werden. Sie implementiert {@link Principal}, damit
 * sie über den {@link javax.ws.rs.core.SecurityContext} von JAX-RS in den
 * Endpunkten zur Verfügung steht.
 *
 * @author benligil
 */
public final class AuthenticatedUser implements Principal {

   private final String username;
   private final Set<String> groups;

   /**
    * Erzeugt einen angemeldeten Benutzer.
    *
    * @param username Der Anmeldename des Benutzers
    * @param groups Die Namen der Gruppen, denen der Benutzer angehört
    */
   public AuthenticatedUser(final String username, final Set<String> groups) {
      this.username = username;
      this.groups = Collections.unmodifiableSet(new LinkedHashSet<>(groups));
   }

   @Override
   public String getName() {
      return username;
   }

   /**
    * Liefert die Gruppen des Benutzers.
    *
    * @return Die unveränderliche Menge der Gruppennamen
    */
   public Set<String> getGroups() {
      return groups;
   }

   /**
    * Prüft, ob der Benutzer einer bestimmten Gruppe angehört.
    *
    * @param group Der zu prüfende Gruppenname
    * @return true, falls der Benutzer Mitglied der Gruppe ist
    */
   public boolean isMemberOf(final String group) {
      return groups.contains(group);
   }

   @Override
   public String toString() {
      return username + groups;
   }
}
