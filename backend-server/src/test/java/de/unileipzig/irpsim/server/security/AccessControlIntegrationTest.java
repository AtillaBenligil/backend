package de.unileipzig.irpsim.server.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import de.unileipzig.irpsim.core.security.Permission;
import de.unileipzig.irpsim.core.security.ResourceType;
import de.unileipzig.irpsim.core.utils.TestFiles;
import de.unileipzig.irpsim.server.endpoints.OptimisationParametersTest;
import de.unileipzig.irpsim.server.utils.RESTCaller;
import de.unileipzig.irpsim.server.utils.ServerTestUtils;
import de.unileipzig.irpsim.server.utils.ServerTests;

/**
 * Prüft die Rechteverwaltung gegen die echte Datenbank.
 *
 * Die übrigen Tests der Sicherheitsschicht verwenden eine Rechteablage im
 * Arbeitsspeicher. Hier laufen Gruppenverwaltung und Eigentümereintrag über
 * das Backend bis in die MariaDB-Datenbank der Testumgebung. Der Testbenutzer
 * aus {@link ServerTestUtils} gehört der Administratorgruppe an.
 *
 * @author benligil
 */
public final class AccessControlIntegrationTest extends ServerTests {

   private static final String TEST_USER = "testbenutzer";

   /**
    * Ein Administrator muss eine Gruppe anlegen, Mitglieder aufnehmen und
    * entfernen sowie die Gruppe wieder löschen können.
    */
   @Test
   public void testAdministratorManagesGroup() {
      final String group = "testgruppe-" + System.currentTimeMillis();
      final String groupsUri = ServerTestUtils.URI + "groups";

      assertEquals(200, RESTCaller.callPutResponse(groupsUri, "{\"name\":\"" + group + "\",\"description\":\"Test\"}").getStatus());
      assertEquals(409, RESTCaller.callPutResponse(groupsUri, "{\"name\":\"" + group + "\"}").getStatus());

      assertEquals(200, RESTCaller.callPost(groupsUri + "/" + group + "/members/bob", "").getStatus());
      assertTrue(members(group).toList().contains("bob"));

      assertEquals(200, RESTCaller.callDeleteResponse(groupsUri + "/" + group + "/members/bob").getStatus());
      assertFalse(members(group).toList().contains("bob"));

      assertEquals(204, RESTCaller.callDeleteResponse(groupsUri + "/" + group).getStatus());
      assertEquals(404, RESTCaller.callDeleteResponse(groupsUri + "/" + group).getStatus());
   }

   /**
    * Wer einen Simulationsauftrag startet, muss dessen Eigentümer werden;
    * andere Benutzer dürfen ihn nicht verändern.
    *
    * Ohne GAMS scheitert die Optimierung selbst, der Eigentümereintrag wird
    * jedoch bereits beim Start gespeichert.
    *
    * @throws IOException Falls das Testszenario nicht gelesen werden kann
    */
   @Test
   public void testStartedJobBelongsToItsCreator() throws IOException {
      final long jobId = ServerTestUtils.startSimulation(new String(Files.readAllBytes(TestFiles.TEST.make().toPath())));

      assertTrue(ResourceAccess.getService().isPermitted(TEST_USER, Collections.emptySet(), ResourceType.JOB, jobId, Permission.WRITE));
      assertFalse(ResourceAccess.getService().isPermitted("fremder", Collections.emptySet(), ResourceType.JOB, jobId, Permission.WRITE));
      assertFalse(ResourceAccess.getService().isPermitted("fremder", Collections.emptySet(), ResourceType.JOB, jobId, Permission.READ));
   }

   /**
    * Der Eigentümer muss ein Szenario für andere freigeben und die Freigabe
    * wieder entziehen können; das letzte Schreibrecht bleibt dabei erhalten.
    */
   @Test
   public void testOwnerSharesScenario() {
      final int scenarioId = createScenario();
      final String accessUri = ServerTestUtils.URI + "access/SCENARIO/" + scenarioId;
      final String carolToken = sessionFor("carol");

      assertEquals(403, getAs(ServerTestUtils.SZENARIEN_URI + "/" + scenarioId, carolToken).getStatus());

      final Response shared = RESTCaller.callPutResponse(accessUri, "{\"subjectType\":\"USER\",\"subjectName\":\"carol\",\"permission\":\"READ\"}");
      assertEquals(200, shared.getStatus());
      assertEquals(2, new JSONArray(shared.readEntity(String.class)).length());
      assertEquals(200, getAs(ServerTestUtils.SZENARIEN_URI + "/" + scenarioId, carolToken).getStatus());

      // Mit Leserecht darf carol die Freigaben weder sehen noch ändern.
      assertEquals(403, getAs(accessUri, carolToken).getStatus());

      assertEquals(409, RESTCaller.callDeleteResponse(accessUri + "/USER/" + TEST_USER).getStatus());
      assertEquals(204, RESTCaller.callDeleteResponse(accessUri + "/USER/carol").getStatus());
      assertEquals(404, RESTCaller.callDeleteResponse(accessUri + "/USER/carol").getStatus());
      assertEquals(403, getAs(ServerTestUtils.SZENARIEN_URI + "/" + scenarioId, carolToken).getStatus());
   }

   /**
    * Unvollständige Angaben und unbekannte Ressourcen müssen abgewiesen werden,
    * damit keine Rechte an nicht vorhandenen Kennungen entstehen.
    */
   @Test
   public void testInvalidSharingRequestsAreRejected() {
      final int scenarioId = createScenario();

      assertEquals(400, RESTCaller.callPutResponse(ServerTestUtils.URI + "access/SCENARIO/" + scenarioId, "{\"subjectName\":\"carol\"}").getStatus());
      assertEquals(404, RESTCaller.callPutResponse(ServerTestUtils.URI + "access/SCENARIO/" + Integer.MAX_VALUE,
            "{\"subjectType\":\"USER\",\"subjectName\":\"carol\",\"permission\":\"READ\"}").getStatus());
      assertEquals(404, RESTCaller.callGetResponse(ServerTestUtils.URI + "access/UNBEKANNT/" + scenarioId).getStatus());
   }

   private static int createScenario() {
      final Response response = RESTCaller.callPutResponse(ServerTestUtils.SZENARIEN_URI, OptimisationParametersTest.EXAMPLEPARAMETERSET);
      assertEquals(200, response.getStatus());
      return new JSONObject(response.readEntity(String.class)).getInt("id");
   }

   /**
    * Legt eine Sitzung für einen weiteren Benutzer ohne Administratorrolle an.
    *
    * Der Testserver läuft im selben Prozess, daher kann die Sitzung wie in
    * {@link ServerTestUtils} unmittelbar erzeugt werden.
    */
   private static String sessionFor(final String username) {
      return SecurityComponents.getSessionManager().create(new AuthenticatedUser(username, Collections.emptySet())).getToken();
   }

   /**
    * Sendet eine Anfrage als anderer Benutzer. RESTCaller wird hier bewusst
    * nicht verwendet, da er stets das Token des Testbenutzers mitsendet.
    */
   private Response getAs(final String uri, final String token) {
      return getJerseyClient().target(uri).request(MediaType.APPLICATION_JSON)
            .header(AuthenticationFilter.AUTHORIZATION_HEADER, AuthenticationFilter.TOKEN_PREFIX + token).get();
   }

   private static JSONArray members(final String group) {
      final Response response = RESTCaller.callGetResponse(ServerTestUtils.URI + "groups");
      assertEquals(200, response.getStatus());
      final JSONArray groups = new JSONArray(response.readEntity(String.class));
      for (int i = 0; i < groups.length(); i++) {
         final JSONObject candidate = groups.getJSONObject(i);
         if (group.equals(candidate.getString("name"))) {
            return candidate.getJSONArray("members");
         }
      }
      throw new AssertionError("Gruppe " + group + " nicht gefunden");
   }
}
