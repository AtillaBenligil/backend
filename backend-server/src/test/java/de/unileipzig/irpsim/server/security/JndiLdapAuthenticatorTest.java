package de.unileipzig.irpsim.server.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.Entry;

/**
 * Prüft die Anmeldung gegen ein LDAP-Verzeichnis.
 *
 * Für die Tests wird ein vollständiger Verzeichnisserver im Arbeitsspeicher
 * gestartet und mit denselben Einträgen befüllt, die auch die
 * Entwicklungsumgebung enthält. Dadurch werden der Bind-Vorgang, die
 * Gruppensuche und die Passwortänderung tatsächlich ausgeführt und nicht nur
 * durch eine Attrappe nachgebildet.
 *
 * @author benligil
 */
public class JndiLdapAuthenticatorTest {

   private static final String BASE_DN = "dc=irpsim,dc=uni-leipzig,dc=de";

   private InMemoryDirectoryServer directory;
   private JndiLdapAuthenticator authenticator;

   /**
    * Startet vor jedem Test einen Verzeichnisserver mit Benutzern und Gruppen.
    *
    * @throws Exception Falls der Server nicht gestartet werden kann
    */
   @Before
   public void startDirectory() throws Exception {
      final InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE_DN);
      config.addAdditionalBindCredentials("cn=Directory Manager", "verwaltung");
      config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("test", 0));
      config.setSchema(null);

      directory = new InMemoryDirectoryServer(config);
      directory.startListening();

      directory.add(new Entry("dn: " + BASE_DN, "objectClass: top", "objectClass: domain", "dc: irpsim"));
      directory.add(new Entry("dn: ou=people," + BASE_DN, "objectClass: organizationalUnit", "ou: people"));
      directory.add(new Entry("dn: ou=groups," + BASE_DN, "objectClass: organizationalUnit", "ou: groups"));

      directory.add(new Entry("dn: uid=bob,ou=people," + BASE_DN,
            "objectClass: inetOrgPerson", "uid: bob", "cn: Bob Modeller", "sn: Modeller", "userPassword: bob123"));
      directory.add(new Entry("dn: uid=carol,ou=people," + BASE_DN,
            "objectClass: inetOrgPerson", "uid: carol", "cn: Carol Viewer", "sn: Viewer", "userPassword: carol123"));

      directory.add(new Entry("dn: cn=irpsim-modellers,ou=groups," + BASE_DN,
            "objectClass: groupOfNames", "cn: irpsim-modellers", "member: uid=bob,ou=people," + BASE_DN));
      directory.add(new Entry("dn: cn=irpsim-viewers,ou=groups," + BASE_DN,
            "objectClass: groupOfNames", "cn: irpsim-viewers", "member: uid=carol,ou=people," + BASE_DN));

      final LdapConfiguration configuration = new LdapConfiguration(
            "ldap://localhost:" + directory.getListenPort(), BASE_DN, "ou=people", "ou=groups");
      authenticator = new JndiLdapAuthenticator(configuration);
   }

   /**
    * Beendet den Verzeichnisserver nach jedem Test.
    */
   @After
   public void stopDirectory() {
      if (directory != null) {
         directory.shutDown(true);
      }
   }

   /**
    * Gültige Zugangsdaten müssen den Benutzer samt seiner Gruppen liefern.
    *
    * @throws Exception Falls die Anmeldung unerwartet fehlschlägt
    */
   @Test
   public void testLoginReturnsUserWithGroups() throws Exception {
      final AuthenticatedUser user = authenticator.authenticate("bob", "bob123");

      assertEquals("bob", user.getName());
      assertTrue(user.isMemberOf("irpsim-modellers"));
      assertFalse(user.isMemberOf("irpsim-viewers"));
   }

   /**
    * Ein falsches Passwort muss abgelehnt werden.
    */
   @Test
   public void testWrongPasswordIsRejected() {
      try {
         authenticator.authenticate("bob", "falsch");
         fail("Die Anmeldung mit falschem Passwort muss abgelehnt werden");
      } catch (final AuthenticationException e) {
         assertEquals("Anmeldung fehlgeschlagen", e.getMessage());
      }
   }

   /**
    * Ein unbekannter Benutzer muss abgelehnt werden.
    */
   @Test
   public void testUnknownUserIsRejected() {
      try {
         authenticator.authenticate("mallory", "beliebig");
         fail("Die Anmeldung eines unbekannten Benutzers muss abgelehnt werden");
      } catch (final AuthenticationException e) {
         assertEquals("Anmeldung fehlgeschlagen", e.getMessage());
      }
   }

   /**
    * Leere Zugangsdaten dürfen nicht zu einer anonymen Anmeldung führen.
    */
   @Test
   public void testEmptyCredentialsAreRejected() {
      try {
         authenticator.authenticate("bob", "");
         fail("Leere Zugangsdaten müssen abgelehnt werden");
      } catch (final AuthenticationException e) {
         assertEquals("Benutzername und Passwort dürfen nicht leer sein", e.getMessage());
      }
   }

   /**
    * Nach einer Passwortänderung darf nur noch das neue Passwort gelten.
    *
    * @throws Exception Falls die Änderung unerwartet fehlschlägt
    */
   @Test
   public void testPasswordChangeTakesEffect() throws Exception {
      authenticator.changePassword("bob", "bob123", "neuesGeheimnis");

      final AuthenticatedUser user = authenticator.authenticate("bob", "neuesGeheimnis");
      assertEquals("bob", user.getName());

      try {
         authenticator.authenticate("bob", "bob123");
         fail("Das alte Passwort darf nach der Änderung nicht mehr gelten");
      } catch (final AuthenticationException e) {
         assertEquals("Anmeldung fehlgeschlagen", e.getMessage());
      }
   }

   /**
    * Eine Passwortänderung mit falschem bisherigen Passwort muss scheitern.
    */
   @Test
   public void testPasswordChangeRequiresOldPassword() {
      try {
         authenticator.changePassword("bob", "falsch", "neuesGeheimnis");
         fail("Die Änderung ohne korrektes bisheriges Passwort muss abgelehnt werden");
      } catch (final AuthenticationException e) {
         assertEquals("Passwortänderung fehlgeschlagen", e.getMessage());
      }
   }
}
