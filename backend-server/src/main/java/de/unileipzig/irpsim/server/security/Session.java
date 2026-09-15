package de.unileipzig.irpsim.server.security;

import java.time.Duration;
import java.time.Instant;

/**
 * Eine aktive Anmeldesitzung.
 *
 * Die Sitzung verknüpft ein Zugriffstoken mit dem angemeldeten Benutzer und
 * verfällt nach einer festen Zeitspanne ohne Zugriff. Dadurch wird ein
 * vergessener Abmeldevorgang nicht zu einem dauerhaft gültigen Token.
 *
 * @author benligil
 */
public final class Session {

   private final String token;
   private final AuthenticatedUser user;
   private final Duration timeout;
   private Instant lastAccess;

   /**
    * Erzeugt eine Sitzung für einen angemeldeten Benutzer.
    *
    * @param token Das Zugriffstoken der Sitzung
    * @param user Der angemeldete Benutzer
    * @param timeout Die Zeitspanne, nach der die Sitzung ohne Zugriff verfällt
    * @param now Der Zeitpunkt der Anmeldung
    */
   public Session(final String token, final AuthenticatedUser user, final Duration timeout, final Instant now) {
      this.token = token;
      this.user = user;
      this.timeout = timeout;
      this.lastAccess = now;
   }

   public String getToken() {
      return token;
   }

   public AuthenticatedUser getUser() {
      return user;
   }

   /**
    * Prüft, ob die Sitzung zum übergebenen Zeitpunkt verfallen ist.
    *
    * @param now Der zu prüfende Zeitpunkt
    * @return true, falls die Sitzung nicht mehr gültig ist
    */
   public boolean isExpired(final Instant now) {
      return lastAccess.plus(timeout).isBefore(now);
   }

   /**
    * Setzt die Verfallszeit der Sitzung zurück.
    *
    * @param now Der Zeitpunkt des Zugriffs
    */
   public void touch(final Instant now) {
      this.lastAccess = now;
   }
}
