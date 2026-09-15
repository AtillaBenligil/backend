package de.unileipzig.irpsim.server.security;

/**
 * Zentraler Zugriffspunkt auf die Sicherheitskomponenten des Backends.
 *
 * Die Endpunkte und der Anmeldefilter werden von JAX-RS erzeugt und können
 * daher keine Abhängigkeiten über den Konstruktor erhalten. Statt eine
 * vollständige Abhängigkeitsinjektion einzuführen, hält diese Klasse die
 * beiden zustandsbehafteten Komponenten -- die Verzeichnisanbindung und die
 * Sitzungsverwaltung -- an einer Stelle. Die Tests können die Komponenten über
 * {@link #initialise(LdapAuthenticator, SessionManager)} durch eingebettete
 * Varianten ersetzen.
 *
 * @author benligil
 */
public final class SecurityComponents {

   private static LdapAuthenticator authenticator;
   private static SessionManager sessionManager;

   private SecurityComponents() {

   }

   /**
    * Initialisiert die Sicherheitskomponenten für den Betrieb.
    *
    * Die Verzeichnisanbindung wird dabei aus den Umgebungsvariablen
    * konfiguriert.
    */
   public static synchronized void initialise() {
      initialise(new JndiLdapAuthenticator(LdapConfiguration.fromEnvironment()), new SessionManager());
   }

   /**
    * Initialisiert die Sicherheitskomponenten mit vorgegebenen Instanzen.
    *
    * @param newAuthenticator Die zu verwendende Verzeichnisanbindung
    * @param newSessionManager Die zu verwendende Sitzungsverwaltung
    */
   public static synchronized void initialise(final LdapAuthenticator newAuthenticator, final SessionManager newSessionManager) {
      authenticator = newAuthenticator;
      sessionManager = newSessionManager;
   }

   /**
    * Liefert die Verzeichnisanbindung.
    *
    * @return Die konfigurierte Verzeichnisanbindung
    */
   public static synchronized LdapAuthenticator getAuthenticator() {
      if (authenticator == null) {
         initialise();
      }
      return authenticator;
   }

   /**
    * Liefert die Sitzungsverwaltung.
    *
    * @return Die konfigurierte Sitzungsverwaltung
    */
   public static synchronized SessionManager getSessionManager() {
      if (sessionManager == null) {
         initialise();
      }
      return sessionManager;
   }
}
