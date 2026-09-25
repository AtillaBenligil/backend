package de.unileipzig.irpsim.server.data.modeldefinitions;

import de.unileipzig.irpsim.server.data.simulationparameters.ScenarioEndpoint;
import de.unileipzig.irpsim.server.optimisation.comparators.modelconnectors.ConnectionType;
import de.unileipzig.irpsim.server.optimisation.comparators.modelconnectors.SyncableParameter;
import de.unileipzig.irpsim.server.optimisation.comparators.modelconnectors.SyncableParameterHandler;
import de.unileipzig.irpsim.server.utils.RESTCaller;
import de.unileipzig.irpsim.server.utils.ServerTestUtils;
import de.unileipzig.irpsim.server.utils.ServerTests;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;
import org.json.JSONObject;
import org.junit.Test;

import javax.ws.rs.core.Response;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Scanner;

import static org.junit.Assert.*;

public class ModelDefinitionsEndpointTest extends ServerTests {

   @Test
   public void getModelDefinitions() {
      Response response = RESTCaller.callGetResponse(ServerTestUtils.URI + "modeldefinitions/");

      assertEquals(200, response.getStatus());

      String content = response.readEntity(String.class);
      int countSubmodels = StringUtils.countMatches(content, "submodels");
      int countIds = StringUtils.countMatches(content, "id") -  countSubmodels;

      URL url = ModelDefinitionsEndpoint.class.getResource("/modeldefinitions/");
      File dict = new File(url.getPath());
      boolean isDict = dict.isDirectory();
      assertTrue(isDict);
      int numberOfFiles = dict.list().length;
      assertEquals(numberOfFiles, countIds);
   }

   /*
    * Die Stammdatenmaske laedt die Definitionen aller Modelle auf einmal. Kombinierte Modelle wie opt-act haben keine
    * eigene Definitionsdatei und duerfen die Antwort nicht verhindern.
    */
   @Test
   public void getAllModelDefinitionsSkipsModelsWithoutDefinitionFile() {
      Response response = RESTCaller.callGetResponse(ServerTestUtils.URI + "modeldefinitions?all=true");

      assertEquals(200, response.getStatus());

      JSONObject all = new JSONObject(response.readEntity(String.class));
      for (String id : all.keySet()) {
         assertNotNull("Definitionsdatei für Modell " + id, ModelDefinitionsEndpoint.class.getResource("/modeldefinitions/" + id + ".json"));
         assertTrue(all.getJSONObject(id).getJSONObject("data").has("definitions"));
      }
      assertTrue(all.has("1"));
   }

   @Test
   public void getConcreteModelDefinitions() {

      int id = 1;

      Response response = RESTCaller.callGetResponse(ServerTestUtils.URI + "modeldefinitions/" + id);

      final InputStream input = ScenarioEndpoint.class.getResourceAsStream("/modeldefinitions/" + id + ".json");
      final Scanner scanner = new Scanner(input, "UTF-8");
      final String content = scanner.useDelimiter("\\Z").next();
      scanner.close();

      String callerResponse = response.readEntity(String.class);
      assertEquals(content, callerResponse);

   }

   @Test
   public void getConnectedModelDefinitions() {
      Response response = RESTCaller.callGetResponse(ServerTestUtils.URI+"modeldefinitions/connectedModelDefinition/1/3");

      assertEquals(200, response.getStatus());
      String contentWithMoreHidden = response.readEntity(String.class);

      Response defaultResponse = RESTCaller.callGetResponse(ServerTestUtils.URI+"modeldefinitions/3");
      assertEquals(200, defaultResponse.getStatus());

      String defaultModel = defaultResponse.readEntity(String.class);

      int counterModded = StringUtils.countMatches(contentWithMoreHidden, "hidden");
      int counterDefault = StringUtils.countMatches(defaultModel, "hidden");

      SyncableParameter syncableParameter = SyncableParameterHandler.getInstance()
            .fetchSyncableParameter(new Pair<>(1, 3), ConnectionType.INPUT);

      int minDifference = syncableParameter.getParameterMap().values().stream().mapToInt(Collection::size).sum();
      // System.out.println("differenz = " + (counterModded-counterDefault)+ " min: "+ minDifference);
      assertTrue(counterModded >= counterDefault+minDifference);
   }
}