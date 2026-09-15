package de.unileipzig.irpsim.server.security;

/**
 * Signalisiert, dass eine Anmeldung oder eine Passwortänderung am
 * LDAP-Verzeichnis fehlgeschlagen ist.
 *
 * Die Ursache wird bewusst nicht an den Aufrufer weitergereicht, damit über die
 * Fehlermeldung nicht unterschieden werden kann, ob ein Benutzername
 * existiert oder lediglich das Passwort falsch war.
 *
 * @author benligil
 */
public class AuthenticationException extends Exception {

   private static final long serialVersionUID = 1L;

   /**
    * Erzeugt eine Ausnahme mit einer Beschreibung des Fehlers.
    *
    * @param message Die Beschreibung des Fehlers
    */
   public AuthenticationException(final String message) {
      super(message);
   }

   /**
    * Erzeugt eine Ausnahme mit Beschreibung und auslösendem Fehler.
    *
    * @param message Die Beschreibung des Fehlers
    * @param cause Der auslösende Fehler
    */
   public AuthenticationException(final String message, final Throwable cause) {
      super(message, cause);
   }
}
