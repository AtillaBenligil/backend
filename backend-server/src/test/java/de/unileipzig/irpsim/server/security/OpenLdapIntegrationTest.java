package de.unileipzig.irpsim.server.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * Prüft die Anmeldung gegen den OpenLDAP-Server, den die Buildautomatisierung
 * über startldap.sh startet.
 *
 * Anders als der eingebettete Server wendet OpenLDAP echte Zugriffsregeln an.
 * Nur hier zeigt sich, ob Anmeldung, Gruppensuche und Passwortänderung unter
 * den Bedingungen des Betriebs funktionieren. Die Einträge stammen aus
 * denselben LDIF-Dateien wie die Entwicklungsumgebung.
 *
 * Wurde kein Server gestartet, etwa bei einem Build mit -Dexec.skip, wird der
 * Test übersprungen statt fehlzuschlagen.
 *
 * @author benligil
 */
public class OpenLdapIntegrationTest {

   private static final String BASE_DN = "dc=irpsim,dc=uni-leipzig,dc=de";

   /** Das Dienstkonto, das startldap.sh im Container einrichtet. */
   private static final String SERVICE_DN = "cn=readonly," + BASE_DN;
   private static final String SERVICE_PASSWORD = "r3ad0nly";

   private String url;

   /**
    * Liest die URL des gestarteten Servers.
    *
    * @throws IOException Falls die Datei nicht gelesen werden kann
    */
   @Before
   public void readServerUrl() throws IOException {
      url = System.getenv("IRPSIM_LDAP_URL_TEST");
      if (url == null) {
         final Path file = Paths.get("ldapurl.txt");
         Assume.assumeTrue("Kein LDAP-Testserver gestartet (ldapurl.txt fehlt)", Files.exists(file));
         url = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
      }
   }

   /**
    * Ein Benutzer muss sich mit seinem Passwort anmelden können und seine
    * Gruppen erhalten.
    *
    * @throws Exception Falls die Anmeldung unerwartet fehlschlägt
    */
   @Test
   public void testLoginReturnsGroups() throws Exception {
      final AuthenticatedUser user = authenticator().authenticate("alice", "alice123");

      assertEquals("alice", user.getName());
      assertTrue(user.isMemberOf("irpsim-admins"));
      assertFalse(user.isMemberOf("irpsim-viewers"));
   }

   /**
    * Ein falsches Passwort muss abgelehnt werden.
    */
   @Test
   public void testWrongPasswordIsRejected() {
      try {
         authenticator().authenticate("alice", "falsch");
         fail("Die Anmeldung mit falschem Passwort muss abgelehnt werden");
      } catch (final AuthenticationException e) {
         assertEquals("Anmeldung fehlgeschlagen", e.getMessage());
      }
   }

   /**
    * Ohne Dienstkonto verhindern die Zugriffsregeln von OpenLDAP die
    * Gruppensuche und damit jede Anmeldung.
    *
    * Der Test belegt, warum das Dienstkonto erforderlich ist.
    */
   @Test
   public void testLoginWithoutServiceAccountFails() {
      final LdapConfiguration configuration = new LdapConfiguration(url, BASE_DN, "ou=people", "ou=groups");
      try {
         new JndiLdapAuthenticator(configuration).authenticate("alice", "alice123");
         fail("OpenLDAP darf normalen Benutzern die Gruppensuche nicht erlauben");
      } catch (final AuthenticationException e) {
         assertEquals("Anmeldung fehlgeschlagen", e.getMessage());
      }
   }

   /**
    * Nach einer Passwortänderung darf nur noch das neue Passwort gelten.
    *
    * Das ursprüngliche Passwort wird am Ende wiederhergestellt, damit der Test
    * gegen denselben Server wiederholt werden kann.
    *
    * @throws Exception Falls die Änderung unerwartet fehlschlägt
    */
   @Test
   public void testPasswordChangeTakesEffect() throws Exception {
      final JndiLdapAuthenticator authenticator = authenticator();
      authenticator.changePassword("bob", "bob123", "neuesGeheimnis");
      try {
         assertEquals("bob", authenticator.authenticate("bob", "neuesGeheimnis").getName());
         try {
            authenticator.authenticate("bob", "bob123");
            fail("Das alte Passwort darf nach der Änderung nicht mehr gelten");
         } catch (final AuthenticationException e) {
            assertEquals("Anmeldung fehlgeschlagen", e.getMessage());
         }
      } finally {
         authenticator.changePassword("bob", "neuesGeheimnis", "bob123");
      }
   }

   /**
    * Die Übernahme der verantwortlichen Personen sucht deren Anmeldenamen über
    * die E-Mail-Adresse; das muss unter den Zugriffsregeln von OpenLDAP mit dem
    * Dienstkonto funktionieren.
    *
    * @throws Exception Falls die Suche unerwartet fehlschlägt
    */
   @Test
   public void testUsernameIsFoundByMail() throws Exception {
      assertEquals(java.util.Optional.of("alice"), authenticator().findUsernameByMail("alice@irpsim.uni-leipzig.de"));
      assertEquals(java.util.Optional.empty(), authenticator().findUsernameByMail("unbekannt@test.de"));
   }

   private JndiLdapAuthenticator authenticator() {
      return new JndiLdapAuthenticator(new LdapConfiguration(url, BASE_DN, "ou=people", "ou=groups", SERVICE_DN, SERVICE_PASSWORD));
   }
}
