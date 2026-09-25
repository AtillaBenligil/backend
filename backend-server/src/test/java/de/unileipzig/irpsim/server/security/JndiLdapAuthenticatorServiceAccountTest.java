package de.unileipzig.irpsim.server.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedSearchRequest;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedSimpleBindResult;
import com.unboundid.ldap.listener.interceptor.InMemoryOperationInterceptor;
import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;

/**
 * Prüft die Gruppensuche über ein Dienstkonto.
 *
 * Der eingebettete Verzeichnisserver kennt von sich aus keine
 * Zugriffsbeschränkungen. Damit der Test die Bedingungen von OpenLDAP
 * abbildet, weist ein Interceptor wie dessen Standardeinstellung jede Suche
 * unterhalb der Gruppen ab, die nicht vom Dienstkonto stammt. Ohne diese
 * Nachbildung ist der Fehler, dass sich unter OpenLDAP niemand anmelden kann,
 * in den Tests nicht sichtbar.
 *
 * @author benligil
 */
public class JndiLdapAuthenticatorServiceAccountTest {

   private static final String BASE_DN = "dc=irpsim,dc=uni-leipzig,dc=de";
   private static final String GROUP_BASE_DN = "ou=groups," + BASE_DN;
   private static final String SERVICE_DN = "cn=readonly," + BASE_DN;
   private static final String SERVICE_PASSWORD = "nurLesen";

   private InMemoryDirectoryServer directory;

   /**
    * Startet vor jedem Test ein Verzeichnis, das die Gruppen nur dem
    * Dienstkonto zeigt.
    *
    * @throws Exception Falls der Server nicht gestartet werden kann
    */
   @Before
   public void startDirectory() throws Exception {
      final InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE_DN);
      config.addAdditionalBindCredentials(SERVICE_DN, SERVICE_PASSWORD);
      config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("test", 0));
      config.setSchema(null);
      config.addInMemoryOperationInterceptor(new GroupsOnlyForServiceAccount());

      directory = new InMemoryDirectoryServer(config);
      directory.startListening();

      directory.add(new Entry("dn: " + BASE_DN, "objectClass: top", "objectClass: domain", "dc: irpsim"));
      directory.add(new Entry("dn: ou=people," + BASE_DN, "objectClass: organizationalUnit", "ou: people"));
      directory.add(new Entry("dn: " + GROUP_BASE_DN, "objectClass: organizationalUnit", "ou: groups"));
      directory.add(new Entry("dn: uid=bob,ou=people," + BASE_DN,
            "objectClass: inetOrgPerson", "uid: bob", "cn: Bob Modeller", "sn: Modeller", "userPassword: bob123",
            "mail: bob@irpsim.uni-leipzig.de"));
      directory.add(new Entry("dn: cn=irpsim-modellers," + GROUP_BASE_DN,
            "objectClass: groupOfNames", "cn: irpsim-modellers", "member: uid=bob,ou=people," + BASE_DN));
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
    * Mit Dienstkonto muss die Anmeldung gelingen und die Gruppen liefern,
    * obwohl der Benutzer die Gruppen selbst nicht lesen darf.
    *
    * @throws Exception Falls die Anmeldung unerwartet fehlschlägt
    */
   @Test
   public void testGroupsAreReadWithServiceAccount() throws Exception {
      final AuthenticatedUser user = authenticator(SERVICE_PASSWORD).authenticate("bob", "bob123");

      assertEquals("bob", user.getName());
      assertTrue(user.isMemberOf("irpsim-modellers"));
   }

   /**
    * Ohne Dienstkonto scheitert die Gruppensuche und damit die Anmeldung.
    *
    * Der Test hält den Fehler fest, der mit dem Dienstkonto behoben wurde.
    */
   @Test
   public void testLoginFailsWithoutServiceAccount() {
      final LdapConfiguration configuration = new LdapConfiguration(url(), BASE_DN, "ou=people", "ou=groups");
      try {
         new JndiLdapAuthenticator(configuration).authenticate("bob", "bob123");
         fail("Ohne Dienstkonto darf die Gruppensuche in diesem Verzeichnis nicht gelingen");
      } catch (final AuthenticationException e) {
         assertEquals("Anmeldung fehlgeschlagen", e.getMessage());
      }
   }

   /**
    * Das Dienstkonto darf die Passwortprüfung des Benutzers nicht ersetzen.
    */
   @Test
   public void testWrongUserPasswordIsStillRejected() {
      try {
         authenticator(SERVICE_PASSWORD).authenticate("bob", "falsch");
         fail("Ein falsches Benutzerpasswort muss auch mit Dienstkonto abgelehnt werden");
      } catch (final AuthenticationException e) {
         assertEquals("Anmeldung fehlgeschlagen", e.getMessage());
      }
   }

   /**
    * Ein abgewiesenes Dienstkonto muss die Anmeldung verhindern, statt einen
    * Benutzer ohne Gruppen anzumelden.
    */
   @Test
   public void testRejectedServiceAccountPreventsLogin() {
      try {
         authenticator("falsch").authenticate("bob", "bob123");
         fail("Bei abgewiesenem Dienstkonto darf keine Anmeldung ohne Gruppen erfolgen");
      } catch (final AuthenticationException e) {
         assertEquals("Anmeldung fehlgeschlagen", e.getMessage());
      }
   }

   /**
    * Über das Dienstkonto muss sich zu einer E-Mail-Adresse der Anmeldename
    * finden lassen; unbekannte Adressen liefern kein Ergebnis.
    *
    * @throws Exception Falls die Suche unerwartet fehlschlägt
    */
   @Test
   public void testUsernameIsFoundByMail() throws Exception {
      assertEquals(java.util.Optional.of("bob"), authenticator(SERVICE_PASSWORD).findUsernameByMail("bob@irpsim.uni-leipzig.de"));
      assertEquals(java.util.Optional.empty(), authenticator(SERVICE_PASSWORD).findUsernameByMail("unbekannt@test.de"));
   }

   /**
    * Ohne Dienstkonto kann das Verzeichnis nicht durchsucht werden; das muss
    * als Fehler gemeldet und nicht als "nicht gefunden" gedeutet werden.
    */
   @Test
   public void testMailLookupRequiresServiceAccount() {
      try {
         new JndiLdapAuthenticator(new LdapConfiguration(url(), BASE_DN, "ou=people", "ou=groups")).findUsernameByMail("bob@irpsim.uni-leipzig.de");
         fail("Ohne Dienstkonto muss die Suche abgelehnt werden");
      } catch (final AuthenticationException e) {
         assertTrue(e.getMessage().contains("Dienstkonto"));
      }
   }

   private JndiLdapAuthenticator authenticator(final String servicePassword) {
      return new JndiLdapAuthenticator(new LdapConfiguration(url(), BASE_DN, "ou=people", "ou=groups", SERVICE_DN, servicePassword));
   }

   private String url() {
      return "ldap://localhost:" + directory.getListenPort();
   }

   /**
    * Bildet die Standardzugriffsregel von OpenLDAP für die Gruppen nach.
    *
    * Der Interceptor merkt sich je Verbindung, als wer sie angemeldet ist, und
    * beantwortet Suchen unterhalb der Gruppen mit "No such object", sofern die
    * Verbindung nicht dem Dienstkonto gehört -- genau wie OpenLDAP.
    */
   private static final class GroupsOnlyForServiceAccount extends InMemoryOperationInterceptor {

      private final Map<Long, String> boundDns = new ConcurrentHashMap<>();

      @Override
      public void processSimpleBindResult(final InMemoryInterceptedSimpleBindResult bindResult) {
         if (bindResult.getResult().getResultCode() == ResultCode.SUCCESS) {
            boundDns.put(bindResult.getConnectionID(), bindResult.getRequest().getBindDN());
         }
      }

      @Override
      public void processSearchRequest(final InMemoryInterceptedSearchRequest request) throws LDAPException {
         final DN base = new DN(request.getRequest().getBaseDN());
         final boolean groupSearch = base.equals(new DN(GROUP_BASE_DN)) || base.isDescendantOf(GROUP_BASE_DN, false);
         final String boundDn = boundDns.get(request.getConnectionID());
         final boolean serviceAccount = boundDn != null && new DN(boundDn).equals(new DN(SERVICE_DN));
         if (groupSearch && !serviceAccount) {
            throw new LDAPException(ResultCode.NO_SUCH_OBJECT);
         }
      }
   }
}
