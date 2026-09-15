package de.unileipzig.irpsim.server.security;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Verwaltet die Sitzungen der angemeldeten Benutzer.
 *
 * Die Sitzungen werden im Arbeitsspeicher gehalten, da das Backend als
 * einzelne Instanz betrieben wird. Gespeichert wird dabei ausschließlich das
 * Zugriffstoken mit dem zugehörigen Benutzer und dessen Gruppen -- Passwörter
 * verbleiben im LDAP-Verzeichnis. Beim Neustart des Servers verfallen alle
 * Sitzungen, was für den Betrieb hinnehmbar ist und eine zusätzliche
 * Persistenzschicht erspart.
 *
 * @author benligil
 */
public final class SessionManager {

   private static final Logger LOG = LogManager.getLogger(SessionManager.class);

   private static final Duration DEFAULT_TIMEOUT = Duration.ofHours(8);
   private static final int TOKEN_BYTES = 32;

   private final Map<String, Session> sessions = new ConcurrentHashMap<>();
   private final SecureRandom random = new SecureRandom();
   private final Duration timeout;
   private final Clock clock;

   /**
    * Erzeugt eine Sitzungsverwaltung mit den Standardwerten des Betriebs.
    */
   public SessionManager() {
      this(DEFAULT_TIMEOUT, Clock.systemUTC());
   }

   /**
    * Erzeugt eine Sitzungsverwaltung mit einstellbarer Verfallszeit und Uhr.
    *
    * Der Konstruktor wird von den Tests genutzt, um das Verfallen von
    * Sitzungen ohne Wartezeit prüfen zu können.
    *
    * @param timeout Die Zeitspanne, nach der eine Sitzung ohne Zugriff verfällt
    * @param clock Die Uhr, aus der die Zeitpunkte gelesen werden
    */
   public SessionManager(final Duration timeout, final Clock clock) {
      this.timeout = timeout;
      this.clock = clock;
   }

   /**
    * Legt eine neue Sitzung für einen angemeldeten Benutzer an.
    *
    * @param user Der angemeldete Benutzer
    * @return Die erzeugte Sitzung inklusive Zugriffstoken
    */
   public Session create(final AuthenticatedUser user) {
      final byte[] bytes = new byte[TOKEN_BYTES];
      random.nextBytes(bytes);
      final String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
      final Session session = new Session(token, user, timeout, clock.instant());
      sessions.put(token, session);
      LOG.debug("Sitzung für Benutzer {} angelegt", user.getName());
      return session;
   }

   /**
    * Löst ein Zugriffstoken in den zugehörigen Benutzer auf.
    *
    * Verfallene Sitzungen werden dabei entfernt und wie ein unbekanntes Token
    * behandelt.
    *
    * @param token Das zu prüfende Zugriffstoken
    * @return Der angemeldete Benutzer, falls die Sitzung gültig ist
    */
   public Optional<AuthenticatedUser> resolve(final String token) {
      if (token == null || token.isEmpty()) {
         return Optional.empty();
      }
      final Session session = sessions.get(token);
      if (session == null) {
         return Optional.empty();
      }
      final Instant now = clock.instant();
      if (session.isExpired(now)) {
         sessions.remove(token);
         LOG.debug("Verfallene Sitzung für Benutzer {} entfernt", session.getUser().getName());
         return Optional.empty();
      }
      session.touch(now);
      return Optional.of(session.getUser());
   }

   /**
    * Beendet eine Sitzung.
    *
    * @param token Das Zugriffstoken der zu beendenden Sitzung
    * @return true, falls eine Sitzung entfernt wurde
    */
   public boolean invalidate(final String token) {
      return token != null && sessions.remove(token) != null;
   }

   /**
    * Beendet alle Sitzungen eines Benutzers.
    *
    * Wird nach einer Passwortänderung aufgerufen, damit noch offene Sitzungen
    * mit dem alten Passwort nicht weiterbestehen.
    *
    * @param username Der Anmeldename des Benutzers
    */
   public void invalidateUser(final String username) {
      sessions.values().removeIf(session -> session.getUser().getName().equals(username));
   }

   /**
    * Liefert die Anzahl der aktuell verwalteten Sitzungen.
    *
    * @return Die Anzahl der Sitzungen
    */
   public int size() {
      return sessions.size();
   }
}
