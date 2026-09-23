package de.unileipzig.irpsim.server.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.glassfish.grizzly.http.server.HttpServer;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.Entry;

import de.unileipzig.irpsim.core.security.AccessControlEntry;
import de.unileipzig.irpsim.core.security.AccessControlRepository;
import de.unileipzig.irpsim.core.security.AuthorizationService;
import de.unileipzig.irpsim.core.security.Permission;
import de.unileipzig.irpsim.core.security.ResourceType;
import de.unileipzig.irpsim.core.security.SubjectType;
import de.unileipzig.irpsim.server.ServerStarter;

/**
 * Prüft die Anmeldung über die tatsächliche HTTP-Schnittstelle.
 *
 * Anders als die übrigen Tests der Sicherheitsschicht startet dieser Test den
 * Grizzly-Server des Backends und spricht ihn über HTTP an. Damit werden die
 * Registrierung des Anmeldefilters, die Auswertung des Authorization-Headers
 * und das Zusammenspiel von Endpunkt, Sitzungsverwaltung und Verzeichnis
 * gemeinsam geprüft.
 *
 * Der Test kommt ohne Datenbank aus, da die Anmeldung selbst ausschliesslich
 * gegen das Verzeichnis arbeitet und abgewiesene Anfragen den Endpunkt gar
 * nicht erst erreichen.
 *
 * @author benligil
 */
public class AuthenticationEndpointTest {

   private static final String BASE_DN = "dc=irpsim,dc=uni-leipzig,dc=de";

   private InMemoryDirectoryServer directory;
   private HttpServer server;
   private String baseUri;

   /**
    * Startet Verzeichnis und Backend vor jedem Test.
    *
    * @throws Exception Falls einer der beiden Server nicht startet
    */
   @Before
   public void startServers() throws Exception {
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
      directory.add(new Entry("dn: cn=irpsim-modellers,ou=groups," + BASE_DN,
            "objectClass: groupOfNames", "cn: irpsim-modellers", "member: uid=bob,ou=people," + BASE_DN));

      final int port = freePort();
      baseUri = "http://localhost:" + port + "/" + ServerStarter.BASE_PATH;
      server = ServerStarter.startServer(baseUri);

      // startServer baut die Sicherheitskomponenten aus den Umgebungsvariablen
      // auf; fuer den Test werden sie auf das eingebettete Verzeichnis
      // umgestellt.
      final LdapConfiguration ldapConfiguration = new LdapConfiguration(
            "ldap://localhost:" + directory.getListenPort(), BASE_DN, "ou=people", "ou=groups");
      SecurityComponents.initialise(new JndiLdapAuthenticator(ldapConfiguration), new SessionManager());
   }

   /**
    * Beendet beide Server nach jedem Test.
    */
   @After
   public void stopServers() {
      // Der Dienst wird zurueckgesetzt, damit ein Test die Rechtepruefung der
      // folgenden Tests nicht beeinflusst.
      ResourceAccess.setService(ResourceAccess.createDefaultService());
      if (server != null) {
         server.shutdownNow();
      }
      if (directory != null) {
         directory.shutDown(true);
      }
   }

   /**
    * Eine Anmeldung mit gültigen Zugangsdaten muss ein Token liefern.
    *
    * @throws Exception Falls die Anfrage fehlschlägt
    */
   @Test
   public void testLoginReturnsToken() throws Exception {
      final Response response = post("/auth/login", "{\"username\":\"bob\",\"password\":\"bob123\"}", null);

      assertEquals(200, response.status);
      final JSONObject body = new JSONObject(response.body);
      assertEquals("bob", body.getString("username"));
      assertTrue(body.getString("token").length() > 0);
      assertEquals("irpsim-modellers", body.getJSONArray("groups").getString(0));
   }

   /**
    * Eine Anmeldung mit falschem Passwort muss mit 401 beantwortet werden.
    *
    * @throws Exception Falls die Anfrage fehlschlägt
    */
   @Test
   public void testLoginWithWrongPasswordIsUnauthorised() throws Exception {
      final Response response = post("/auth/login", "{\"username\":\"bob\",\"password\":\"falsch\"}", null);

      assertEquals(401, response.status);
      assertFalse(response.body.contains("token"));
   }

   /**
    * Ein geschützter Endpunkt darf ohne Token nicht erreichbar sein.
    *
    * @throws Exception Falls die Anfrage fehlschlägt
    */
   @Test
   public void testProtectedEndpointRequiresToken() throws Exception {
      assertEquals(401, get("/szenarien", null).status);
      assertEquals(401, get("/groups", null).status);
   }

   /**
    * Ein ungültiges Token darf nicht akzeptiert werden.
    *
    * @throws Exception Falls die Anfrage fehlschlägt
    */
   @Test
   public void testInvalidTokenIsRejected() throws Exception {
      assertEquals(401, get("/szenarien", "voellig-erfunden").status);
   }

   /**
    * Die Versionsinformationen müssen ohne Anmeldung erreichbar bleiben, da sie
    * vor der Anmeldung angezeigt werden.
    *
    * @throws Exception Falls die Anfrage fehlschlägt
    */
   @Test
   public void testPublicEndpointStaysReachable() throws Exception {
      assertFalse(401 == get("/generalinformation/versions", null).status);
   }

   /**
    * Ein gültiges Token muss den Anmeldefilter passieren und die Rechteprüfung
    * erreichen.
    *
    * Geprüft wird die vollständige Kette: Der Anmeldefilter erkennt das Token
    * an und reicht die Anfrage weiter, woraufhin der ResourceAuthorizationFilter den
    * Zugriff mangels Berechtigung mit 403 abweist. Die Unterscheidung zwischen
    * 401 und 403 zeigt, dass die Anmeldung anerkannt und erst die
    * Rechteprüfung negativ ausgefallen ist.
    *
    * @throws Exception Falls die Anfrage fehlschlägt
    */
   @Test
   public void testValidTokenReachesAuthorisationCheck() throws Exception {
      // Das Recht an Auftrag 1 liegt bei einem anderen Benutzer.
      ResourceAccess.setService(new AuthorizationService(new SingleEntryRepository(
            new AccessControlEntry(ResourceType.JOB, 1L, SubjectType.USER, "carol", Permission.READ))));

      final Response login = post("/auth/login", "{\"username\":\"bob\",\"password\":\"bob123\"}", null);
      final String token = new JSONObject(login.body).getString("token");

      assertEquals(403, get("/simulations/1", token).status);
      assertEquals(401, get("/simulations/1", null).status);
   }

   /**
    * Eine Rechteablage mit genau einem Eintrag für die Prüfung der Filterkette.
    */
   private static final class SingleEntryRepository implements AccessControlRepository {

      private final AccessControlEntry entry;

      private SingleEntryRepository(final AccessControlEntry entry) {
         this.entry = entry;
      }

      @Override
      public List<AccessControlEntry> findEntries(final ResourceType resourceType, final long resourceId) {
         return entry.getResourceType() == resourceType && entry.getResourceId() == resourceId
               ? Collections.singletonList(entry)
               : Collections.<AccessControlEntry> emptyList();
      }

      @Override
      public Set<Long> findRestrictedResources(final ResourceType resourceType) {
         return Collections.singleton(entry.getResourceId());
      }

      @Override
      public Set<Long> findPermittedResources(final ResourceType resourceType, final String username, final Set<String> groups,
            final Permission required) {
         return Collections.emptySet();
      }

      @Override
      public Set<String> findGroupNames(final String username) {
         return Collections.emptySet();
      }

      @Override
      public void save(final AccessControlEntry newEntry) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean deleteEntry(final ResourceType resourceType, final long resourceId, final SubjectType subjectType,
            final String subjectName) {
         throw new UnsupportedOperationException();
      }

      @Override
      public void deleteEntries(final ResourceType resourceType, final long resourceId) {
         throw new UnsupportedOperationException();
      }
   }

   /**
    * Benutzer ohne Administratorrolle dürfen Gruppen weder anlegen noch
    * löschen noch deren Mitglieder ändern; sonst könnte sich jeder selbst in
    * eine Gruppe aufnehmen und deren Rechte erlangen.
    *
    * Die Anfragen werden abgewiesen, bevor die Datenbank berührt wird, daher
    * kommt der Test ohne Datenbank aus.
    *
    * @throws Exception Falls die Anfrage fehlschlägt
    */
   @Test
   public void testGroupManagementRequiresAdministrator() throws Exception {
      final Response login = post("/auth/login", "{\"username\":\"bob\",\"password\":\"bob123\"}", null);
      final String token = new JSONObject(login.body).getString("token");

      assertEquals(403, send("PUT", "/groups", "{\"name\":\"eigene\"}", token).status);
      assertEquals(403, send("DELETE", "/groups/irpsim-modellers", null, token).status);
      assertEquals(403, post("/groups/irpsim-modellers/members/bob", "", token).status);
      assertEquals(403, send("DELETE", "/groups/irpsim-modellers/members/carol", null, token).status);
   }

   /**
    * Nach der Abmeldung darf das Token nicht mehr akzeptiert werden.
    *
    * @throws Exception Falls die Anfrage fehlschlägt
    */
   @Test
   public void testLogoutInvalidatesToken() throws Exception {
      final Response login = post("/auth/login", "{\"username\":\"bob\",\"password\":\"bob123\"}", null);
      final String token = new JSONObject(login.body).getString("token");

      assertEquals(204, post("/auth/logout", "", token).status);
      assertEquals(401, get("/szenarien", token).status);
   }

   /**
    * Die Antwort einer HTTP-Anfrage.
    */
   private static final class Response {
      private final int status;
      private final String body;

      private Response(final int status, final String body) {
         this.status = status;
         this.body = body;
      }
   }

   private Response get(final String path, final String token) throws IOException {
      return send("GET", path, null, token);
   }

   private Response post(final String path, final String payload, final String token) throws IOException {
      return send("POST", path, payload, token);
   }

   /**
    * Führt eine HTTP-Anfrage gegen das laufende Backend aus.
    *
    * @param method Das HTTP-Verfahren
    * @param path Der Pfad unterhalb des Basispfads
    * @param payload Der zu sendende Rumpf oder null
    * @param token Das Zugriffstoken oder null
    * @return Status und Rumpf der Antwort
    * @throws IOException Falls die Verbindung fehlschlägt
    */
   private Response send(final String method, final String path, final String payload, final String token) throws IOException {
      final HttpURLConnection connection = (HttpURLConnection) new URL(baseUri + path).openConnection();
      connection.setRequestMethod(method);
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(10000);
      if (token != null) {
         connection.setRequestProperty(AuthenticationFilter.AUTHORIZATION_HEADER, AuthenticationFilter.TOKEN_PREFIX + token);
      }
      if (payload != null) {
         connection.setRequestProperty("Content-Type", "application/json");
         connection.setDoOutput(true);
         try (OutputStream out = connection.getOutputStream()) {
            out.write(payload.getBytes(StandardCharsets.UTF_8));
         }
      }

      final int status = connection.getResponseCode();
      final InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
      return new Response(status, read(stream));
   }

   private static String read(final InputStream stream) throws IOException {
      if (stream == null) {
         return "";
      }
      try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
         final byte[] chunk = new byte[4096];
         int length;
         while ((length = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, length);
         }
         return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
      }
   }

   private static int freePort() throws IOException {
      try (ServerSocket socket = new ServerSocket(0)) {
         return socket.getLocalPort();
      }
   }
}
