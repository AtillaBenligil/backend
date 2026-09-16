package de.unileipzig.irpsim.server.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

/**
 * Prüft die Verwaltung der Anmeldesitzungen.
 *
 * Die Tests verwenden eine steuerbare Uhr, damit das Verfallen einer Sitzung
 * ohne Wartezeit geprüft werden kann.
 *
 * @author benligil
 */
public class SessionManagerTest {

   private static final Duration TIMEOUT = Duration.ofMinutes(30);

   private MutableClock clock;
   private SessionManager manager;
   private AuthenticatedUser user;

   /**
    * Legt vor jedem Test eine Sitzungsverwaltung mit steuerbarer Uhr an.
    */
   @Before
   public void setUp() {
      clock = new MutableClock(Instant.parse("2026-09-16T10:00:00Z"));
      manager = new SessionManager(TIMEOUT, clock);
      user = new AuthenticatedUser("bob", Collections.singleton("irpsim-modellers"));
   }

   /**
    * Ein erzeugtes Token muss auf den angemeldeten Benutzer auflösbar sein.
    */
   @Test
   public void testTokenIsResolvedToUser() {
      final Session session = manager.create(user);

      final Optional<AuthenticatedUser> resolved = manager.resolve(session.getToken());

      assertTrue(resolved.isPresent());
      assertEquals("bob", resolved.get().getName());
      assertTrue(resolved.get().isMemberOf("irpsim-modellers"));
   }

   /**
    * Zwei Anmeldungen dürfen nicht dasselbe Token erhalten.
    */
   @Test
   public void testTokensAreUnique() {
      final Session first = manager.create(user);
      final Session second = manager.create(user);

      assertFalse(first.getToken().equals(second.getToken()));
      assertEquals(2, manager.size());
   }

   /**
    * Ein unbekanntes Token darf keinen Benutzer liefern.
    */
   @Test
   public void testUnknownTokenIsRejected() {
      assertFalse(manager.resolve("nicht-vergeben").isPresent());
      assertFalse(manager.resolve(null).isPresent());
      assertFalse(manager.resolve("").isPresent());
   }

   /**
    * Nach der Abmeldung darf das Token nicht mehr gültig sein.
    */
   @Test
   public void testLogoutInvalidatesToken() {
      final Session session = manager.create(user);

      assertTrue(manager.invalidate(session.getToken()));

      assertFalse(manager.resolve(session.getToken()).isPresent());
      assertEquals(0, manager.size());
   }

   /**
    * Eine Sitzung muss nach Ablauf der Frist ohne Zugriff verfallen.
    */
   @Test
   public void testSessionExpiresAfterTimeout() {
      final Session session = manager.create(user);

      clock.advance(TIMEOUT.plusMinutes(1));

      assertFalse(manager.resolve(session.getToken()).isPresent());
      assertEquals(0, manager.size());
   }

   /**
    * Ein Zugriff innerhalb der Frist muss die Sitzung verlängern.
    */
   @Test
   public void testAccessRenewsSession() {
      final Session session = manager.create(user);

      clock.advance(TIMEOUT.minusMinutes(1));
      assertTrue(manager.resolve(session.getToken()).isPresent());

      clock.advance(TIMEOUT.minusMinutes(1));
      assertTrue(manager.resolve(session.getToken()).isPresent());
   }

   /**
    * Nach einer Passwortänderung müssen alle Sitzungen des Benutzers enden.
    */
   @Test
   public void testAllSessionsOfUserAreInvalidated() {
      final Session first = manager.create(user);
      final Session second = manager.create(user);
      final Session other = manager.create(new AuthenticatedUser("carol", Collections.<String> emptySet()));

      manager.invalidateUser("bob");

      assertFalse(manager.resolve(first.getToken()).isPresent());
      assertFalse(manager.resolve(second.getToken()).isPresent());
      assertTrue(manager.resolve(other.getToken()).isPresent());
   }

   /**
    * Eine Uhr, deren Zeitpunkt im Test vorgestellt werden kann.
    */
   private static final class MutableClock extends Clock {

      private Instant now;

      private MutableClock(final Instant start) {
         this.now = start;
      }

      private void advance(final Duration duration) {
         now = now.plus(duration);
      }

      @Override
      public java.time.ZoneId getZone() {
         return ZoneOffset.UTC;
      }

      @Override
      public Clock withZone(final java.time.ZoneId zone) {
         return this;
      }

      @Override
      public Instant instant() {
         return now;
      }
   }
}
