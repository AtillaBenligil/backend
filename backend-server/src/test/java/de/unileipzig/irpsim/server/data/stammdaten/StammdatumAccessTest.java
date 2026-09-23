package de.unileipzig.irpsim.server.data.stammdaten;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collections;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.unileipzig.irpsim.core.security.Permission;
import de.unileipzig.irpsim.core.security.ResourceType;
import de.unileipzig.irpsim.core.standingdata.data.Stammdatum;
import de.unileipzig.irpsim.server.security.AuthenticatedUser;
import de.unileipzig.irpsim.server.security.AuthenticationFilter;
import de.unileipzig.irpsim.server.security.ResourceAccess;
import de.unileipzig.irpsim.server.security.SecurityComponents;
import de.unileipzig.irpsim.server.utils.RESTCaller;
import de.unileipzig.irpsim.server.utils.ServerTestUtils;
import de.unileipzig.irpsim.server.utils.ServerTests;

/**
 * Prüft die Lese- und Schreibrechte an Stammdaten gegen die echte Datenbank.
 *
 * Die Rechteverwaltung ersetzt die früheren verantwortlichen Personen: Wer ein
 * Stammdatum anlegt, darf es verändern; andere Benutzer sehen es erst, wenn es
 * ihnen oder einer ihrer Gruppen freigegeben wurde. Der Testbenutzer aus
 * {@link ServerTestUtils} legt die Stammdaten an, carol ist ein Benutzer ohne
 * Administratorrolle.
 *
 * @author benligil
 */
public class StammdatumAccessTest extends ServerTests {

   private static final ObjectMapper MAPPER = new ObjectMapper();

   private int id;
   private String carolToken;

   /**
    * Legt vor jedem Test ein Stammdatum des Testbenutzers und eine Sitzung für
    * carol an.
    *
    * @throws IOException Falls das Stammdatum nicht angelegt werden kann
    */
   @Before
   public void createStammdatum() throws IOException {
      StammdatenTestUtil.cleanUp();
      StammdatenTestUtil.createPrognoseszenarien();
      id = StammdatenTestUtil.addStammdatum(StammdatenTestExamples.getThermalLoadExample());
      carolToken = SecurityComponents.getSessionManager().create(new AuthenticatedUser("carol", Collections.emptySet())).getToken();
   }

   /**
    * Wer ein Stammdatum anlegt, muss dessen Schreibrecht erhalten.
    */
   @Test
   public void testCreatorBecomesOwner() {
      assertTrue(ResourceAccess.getService().isPermitted("testbenutzer", Collections.emptySet(), ResourceType.STAMMDATUM, id, Permission.WRITE));
   }

   /**
    * Ohne Freigabe darf ein anderer Benutzer das Stammdatum weder sehen noch
    * in der Auflistung finden noch verändern.
    *
    * @throws IOException Falls das Stammdatum nicht serialisiert werden kann
    */
   @Test
   public void testForeignUserHasNoAccess() throws IOException {
      assertEquals(403, as("GET", "stammdaten/" + id, null).getStatus());
      assertEquals(403, as("GET", "stammdaten/" + id + "/excel", null).getStatus());
      assertEquals(403, as("PUT", "stammdaten/" + id, changedStammdatum()).getStatus());
      assertEquals(403, as("DELETE", "stammdaten/" + id, null).getStatus());
      assertEquals(403, as("GET", "stammdaten/export?ids=" + id, null).getStatus());
      assertFalse(listedIds(as("GET", "stammdaten", null)).toList().contains(id));
   }

   /**
    * Nach einer Freigabe mit Leserecht darf der Benutzer das Stammdatum sehen,
    * aber weiterhin nicht verändern.
    *
    * @throws IOException Falls das Stammdatum nicht serialisiert werden kann
    */
   @Test
   public void testReadPermissionAllowsReadingOnly() throws IOException {
      share("READ");

      assertEquals(200, as("GET", "stammdaten/" + id, null).getStatus());
      assertTrue(listedIds(as("GET", "stammdaten", null)).toList().contains(id));
      assertEquals(403, as("PUT", "stammdaten/" + id, changedStammdatum()).getStatus());
      assertEquals(403, as("DELETE", "stammdaten/" + id, null).getStatus());
   }

   /**
    * Mit Schreibrecht darf der Benutzer das Stammdatum verändern.
    *
    * @throws IOException Falls das Stammdatum nicht serialisiert werden kann
    */
   @Test
   public void testWritePermissionAllowsChanging() throws IOException {
      share("WRITE");

      assertEquals(200, as("PUT", "stammdaten/" + id, changedStammdatum()).getStatus());
   }

   /**
    * Die früheren verantwortlichen Personen dürfen in der Schnittstelle nicht
    * mehr erscheinen und keine Wirkung mehr haben.
    */
   @Test
   public void testResponsiblePersonsAreGone() {
      final Response response = RESTCaller.callGetResponse(ServerTestUtils.URI + "stammdaten/" + id);
      assertEquals(200, response.getStatus());
      assertFalse(response.readEntity(String.class).contains("verantwortlicher"));
   }

   private void share(final String permission) {
      final Response response = RESTCaller.callPutResponse(ServerTestUtils.URI + "access/STAMMDATUM/" + id,
            "{\"subjectType\":\"USER\",\"subjectName\":\"carol\",\"permission\":\"" + permission + "\"}");
      assertEquals(200, response.getStatus());
   }

   private String changedStammdatum() throws IOException {
      final Stammdatum changed = StammdatenTestExamples.getThermalLoadExample();
      changed.setId(id);
      changed.setStandardszenario(true);
      return MAPPER.writeValueAsString(changed);
   }

   private static JSONArray listedIds(final Response response) {
      assertEquals(200, response.getStatus());
      return new JSONArray(response.readEntity(String.class));
   }

   /**
    * Sendet eine Anfrage als carol. RESTCaller wird hier bewusst nicht
    * verwendet, da er stets das Token des Testbenutzers mitsendet. Es wird
    * jeder Antworttyp akzeptiert, da etwa der Excel-Export sonst schon bei der
    * Inhaltsaushandlung mit 406 abgewiesen würde.
    */
   private Response as(final String method, final String path, final String json) {
      final javax.ws.rs.client.Invocation.Builder request = getJerseyClient().target(ServerTestUtils.URI + path).request(MediaType.WILDCARD_TYPE)
            .header(AuthenticationFilter.AUTHORIZATION_HEADER, AuthenticationFilter.TOKEN_PREFIX + carolToken);
      return json == null ? request.method(method) : request.method(method, Entity.entity(json, MediaType.APPLICATION_JSON));
   }
}
