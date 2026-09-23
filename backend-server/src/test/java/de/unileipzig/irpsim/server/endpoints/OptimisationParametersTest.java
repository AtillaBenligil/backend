package de.unileipzig.irpsim.server.endpoints;

import java.io.FileNotFoundException;
import java.io.IOException;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import de.unileipzig.irpsim.core.utils.ParameterBaseDependenciesUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.client.JerseyWebTarget;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.unileipzig.irpsim.core.Constants;
import de.unileipzig.irpsim.core.simulation.data.json.JSONParametersMultimodel;
import de.unileipzig.irpsim.core.testutils.DatabaseTestUtils;
import de.unileipzig.irpsim.core.utils.TestFiles;
import de.unileipzig.irpsim.server.marker.RESTTest;
import de.unileipzig.irpsim.server.utils.RESTCaller;
import de.unileipzig.irpsim.server.utils.ServerTestUtils;
import de.unileipzig.irpsim.server.utils.ServerTests;

/**
 * Testet die simulationsparameters-Schnittstelle.
 *
 * @author reichelt
 */
@Category(RESTTest.class)
public final class OptimisationParametersTest extends ServerTests {

   private static final String EXAMPLEPARAMETERS = "{\"years\": [{\"config\":{\"simulationlength\":8736,\"savelength\":0,\"timestep\":0.0,\"modell\":\"Basismodell\",\"year\":null},"
         + "\"scalars\":{\"sca_VarDemandMax\":300.0,\"sca_FixDemand\":300.0,\"sca_StatCost\":0.0,\"sca_Cop_stat\":3.0},\"sets\":{},"
         + "\"timeseries\":{" + "\"par_VarDemand\":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20],"
         + "\"par_SpotForecast\":[3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22]},\"tables\":{}" + "} ]}";
   private static final String EXAMPLEPARAMETERSMULTI = "{ \"description\": {\n"
         + "        \"businessModelDescription\": \"Standardszenario f\\u00fcr ein kombiniertes Szenario\",\n"
         + "        \"creator\": null,\n"
         + "        \"investmentCustomerSide\": null,\n"
         + "        \"parameterAttention\": null,\n"
         + "        \"supportiveYears\": null\n"
         + "    },\n"
         + "    \"models\": [\n"
         + EXAMPLEPARAMETERS
         + ","
         + "        {\n"
         + "            \"postprocessing\": {\n"
         + "                \"scalars\": {},\n"
         + "                \"sets\": {},\n"
         + "                \"tables\": {}\n"
         + "            },\n"
         + "            \"years\": [\n"
         + "                {\n"
         + "                    \"config\": {\n"
         + "                        \"interpolated\": false,\n"
         + "                        \"modeldefinition\": 3,\n"
         + "                        \"optimizationlength\": 192,\n"
         + "                        \"prognoseszenario\": null,\n"
         + "                        \"resolution\": 35040,\n"
         + "                        \"savelength\": 96,\n"
         + "                        \"simulationlength\": 35040,\n"
         + "                        \"timestep\": 0.25,\n"
         + "                        \"year\": 2015\n"
         + "                    },\n"
         + "                    \"postprocessing\": {\n"
         + "                        \"scalars\": {},\n"
         + "                        \"sets\": {},\n"
         + "                        \"tables\": {}\n"
         + "                    },\n"
         + "                    \"scalars\": {\n"
         + "                        \"sca_global_seed\": 123.0\n"
         + "                    },\n"
         + "                    \"sets\": {\n"
         + "                        \"set_AgentGroup\": {\n"
         + "                            \"gx100x01\": {\n"
         + "                                \"par_AgentGroup_adaptionRate\": 0.01,\n"
         + "                                \"par_AgentGroup_numberOfAgents\": 100.0\n"
         + "                            },\n"
         + "                            \"gx10x5\": {\n"
         + "                                \"par_AgentGroup_adaptionRate\": 0.5,\n"
         + "                                \"par_AgentGroup_numberOfAgents\": 10.0\n"
         + "                            },\n"
         + "                            \"gx50x75\": {\n"
         + "                                \"par_AgentGroup_adaptionRate\": 0.75,\n"
         + "                                \"par_AgentGroup_numberOfAgents\": 50.0\n"
         + "                            }\n"
         + "                        },\n"
         + "                        \"set_Product\": {\n"
         + "                            \"Auto\": {\n"
         + "                                \"par_Product_name\": 0.0\n"
         + "                            },\n"
         + "                            \"Haus\": {\n"
         + "                                \"par_Product_name\": 0.0\n"
         + "                            }\n"
         + "                        }\n"
         + "                    },\n"
         + "                    \"tables\": {},\n"
         + "                    \"timeseries\": {}\n"
         + "                },\n"
         + "                {\n"
         + "                    \"config\": {\n"
         + "                        \"interpolated\": false,\n"
         + "                        \"modeldefinition\": 3,\n"
         + "                        \"optimizationlength\": 192,\n"
         + "                        \"prognoseszenario\": null,\n"
         + "                        \"resolution\": 35040,\n"
         + "                        \"savelength\": 96,\n"
         + "                        \"simulationlength\": 35040,\n"
         + "                        \"timestep\": 0.25,\n"
         + "                        \"year\": 2016\n"
         + "                    },\n"
         + "                    \"postprocessing\": {\n"
         + "                        \"scalars\": {},\n"
         + "                        \"sets\": {},\n"
         + "                        \"tables\": {}\n"
         + "                    },\n"
         + "                    \"scalars\": {\n"
         + "                        \"sca_global_seed\": 123.0\n"
         + "                    },\n"
         + "                    \"sets\": {\n"
         + "                        \"set_AgentGroup\": {\n"
         + "                            \"gx100x01\": {\n"
         + "                                \"par_AgentGroup_adaptionRate\": 0.3,\n"
         + "                                \"par_AgentGroup_numberOfAgents\": 100.0\n"
         + "                            },\n"
         + "                            \"gx10x5\": {\n"
         + "                                \"par_AgentGroup_adaptionRate\": 0.5,\n"
         + "                                \"par_AgentGroup_numberOfAgents\": 10.0\n"
         + "                            },\n"
         + "                            \"gx50x75\": {\n"
         + "                                \"par_AgentGroup_adaptionRate\": 0.75,\n"
         + "                                \"par_AgentGroup_numberOfAgents\": 50.0\n"
         + "                            }\n"
         + "                        },\n"
         + "                        \"set_Product\": {\n"
         + "                            \"Auto\": {\n"
         + "                                \"par_Product_name\": 0.0\n"
         + "                            },\n"
         + "                            \"Haus\": {\n"
         + "                                \"par_Product_name\": 0.0\n"
         + "                            }\n"
         + "                        }\n"
         + "                    },\n"
         + "                    \"tables\": {},\n"
         + "                    \"timeseries\": {}\n"
         + "                }\n"
         + "            ]\n"
         + "        }\n"
         + "    ]"
         + "}";
   public static final String EXAMPLEPARAMETERSET = "{\"name\": \"testKonfig\", \"data\": "
         + "{\"models\": [" + EXAMPLEPARAMETERS + "] }"
         + " }";
   private static final String EXAMPLEPARAMETERSET2 = "{\"name\": \"testKonfig2\",  \"data\": "
         + "{\"models\": [" + EXAMPLEPARAMETERS + "] }"
         + " }";
   private static final String EXAMPLE_UNDELETABLE = "{\"name\": \"testKonfig\", \"deletable\": false, \"data\": "
         + "{\"models\": [" + EXAMPLEPARAMETERS + "] }"
         + " }";
   private static final String EXAMPLEPARAMETERSETMULTI = "{\"name\": \"testKonfigMulti\", \"data\": " + EXAMPLEPARAMETERSMULTI + " }";
   private static final Logger LOG = LogManager.getLogger(OptimisationParametersTest.class);
   private static int initid = 0;

   /**
    * Init.
    * 
    * @throws FileNotFoundException
    */
   @BeforeClass
   public static void initializeDB() throws FileNotFoundException {
      initid = DatabaseTestUtils.initializeParametersetsDatabase(TestFiles.TEST.make());
   }

   /**
    * Testet das Auslesen der Parametersetinfos.
    */
   @Test
   public void testGetList() {
      LOG.debug("TestGetList");
      final JSONObject jsa = getParameterSets();
      Assert.assertNotNull(jsa.get("" + initid));

   }

   @Test
   public void testFilteredList() {
      final JSONObject jsa2 = getParameterSetsFiltered(2);
      Assert.assertFalse(jsa2.has("" + initid));

      final JSONObject jsa3 = getParameterSetsFiltered(1);
      Assert.assertNotNull(jsa3.get("" + initid));
   }

   /**
    * Testet, ob ein Parameterset geladen werden kann.
    */
   @Test
   public void testGetEntity() throws JsonParseException, JsonMappingException, IOException {
      final JSONObject jsa = getParameterSets();
      Assert.assertNotNull(jsa.get("" + initid));
      final JerseyWebTarget jwt = getJerseyClient().target(ServerTestUtils.SZENARIEN_URI + "/" + initid);
      final Response response = RESTCaller.request(jwt).get();

      final String entity = response.readEntity(String.class);
      LOG.debug("Antwort: {}", entity.substring(0, 100));

      final ObjectMapper om = new ObjectMapper();
      final JSONParametersMultimodel gpj = om.readValue(entity, JSONParametersMultimodel.class);

      Assert.assertNotNull(gpj);
   }

   /**
    * Testet, ob ein Parameterset gespeichert werden kann.
    */
   @Test
   public void testPutEntity() throws JsonParseException, JsonMappingException, IOException {
      LOG.debug("uri: {}", ServerTestUtils.SZENARIEN_URI, EXAMPLEPARAMETERSET);
      Response response = RESTCaller.callPutResponse(ServerTestUtils.SZENARIEN_URI, EXAMPLEPARAMETERSET);
      Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
      JSONObject jso = new JSONObject(response.readEntity(String.class));
      final int id = (int) jso.get("id");

      final JSONObject jsa = getParameterSets();
      final JSONObject newlyAddedMetadata = jsa.getJSONObject("" + id);
      Assert.assertNotNull(newlyAddedMetadata);
      Assert.assertEquals(1, newlyAddedMetadata.getInt("modeldefinition"));
      response = RESTCaller.callGetResponse(ServerTestUtils.SZENARIEN_URI + "/" + id);

      final ObjectMapper om = new ObjectMapper();
      JSONParametersMultimodel gpj = om.readValue(response.readEntity(String.class), JSONParametersMultimodel.class);

      Assert.assertNotNull(gpj);

      Response response2 = RESTCaller.callPutResponse(ServerTestUtils.SZENARIEN_URI,
            EXAMPLEPARAMETERSET2);
      Assert.assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());

      jso = new JSONObject(response2.readEntity(String.class));
      final int id2 = (int) jso.get("id");

      Assert.assertEquals(id + 1, id2);

      response2 = RESTCaller.callGetResponse(ServerTestUtils.SZENARIEN_URI + "/" + id2);
      gpj = om.readValue(response2.readEntity(String.class), JSONParametersMultimodel.class);
      Assert.assertNotNull(gpj);
   }

   /**
    * Testet, ob ein Parametersatz ordnungsgemäß gelöscht wird.
    */
   @Test
   public void testDeleteEntity() throws JsonParseException, JsonMappingException, IOException {
      LOG.debug("EXAMPLEPARAMETERSET: {}", EXAMPLEPARAMETERSET);
      LOG.debug("uri: {}", ServerTestUtils.SZENARIEN_URI + "?name=test");
      JerseyWebTarget jwt = getJerseyClient().target(ServerTestUtils.SZENARIEN_URI + "?name=test");
      Response response = RESTCaller.request(jwt).put(Entity.entity(EXAMPLEPARAMETERSET, MediaType.APPLICATION_JSON));
      Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
      String entity = response.readEntity(String.class);
      final JSONObject jso = new JSONObject(entity);
      final int id = (int) jso.get("id");

      final JSONObject jsa = getParameterSets();
      Assert.assertNotNull(jsa.get("" + id));
      jwt = getJerseyClient().target(ServerTestUtils.SZENARIEN_URI + "/" + id);
      response = RESTCaller.request(jwt).get();
      entity = response.readEntity(String.class);

      final ObjectMapper om = new ObjectMapper();
      final JSONParametersMultimodel gpj = om.readValue(entity, JSONParametersMultimodel.class);

      Assert.assertNotNull(gpj);

      LOG.debug("Lade Daten mit Id: {}", id);
      jwt = getJerseyClient().target(ServerTestUtils.SZENARIEN_URI + "/" + id);
      response = RESTCaller.request(jwt).delete();

      LOG.debug("Antwort: " + response.readEntity(String.class));
      Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

      jwt = getJerseyClient().target(ServerTestUtils.SZENARIEN_URI + "/" + id);
      response = RESTCaller.request(jwt).get();
      entity = response.readEntity(String.class);

      Assert.assertNotEquals(Response.Status.OK.getStatusCode(), response.getStatus());
   }

   /**
    * Testst, ob ein als nicht löschbar deklariertes Parameterset tatsächlich nicht gelöscht werden kann.
    */
   @Test
   public void testUnDeletableEntity() throws JsonParseException, JsonMappingException, IOException {
      LOG.debug("text: {}", EXAMPLE_UNDELETABLE);
      LOG.debug("uri: {}", ServerTestUtils.SZENARIEN_URI + "?name=test");
      JerseyWebTarget jwt = getJerseyClient().target(ServerTestUtils.SZENARIEN_URI + "?name=test");
      Response response = RESTCaller.request(jwt).put(Entity.entity(EXAMPLE_UNDELETABLE, MediaType.APPLICATION_JSON));
      Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
      String entity = response.readEntity(String.class);
      final JSONObject jso = new JSONObject(entity);
      final int id = (int) jso.get("id");

      final JSONObject parameterSets = getParameterSets();
      Assert.assertNotNull(parameterSets.get("" + id));
      jwt = getJerseyClient().target(ServerTestUtils.SZENARIEN_URI + "/" + id);
      response = RESTCaller.request(jwt).get();
      entity = response.readEntity(String.class);

      final ObjectMapper om = new ObjectMapper();
      final JSONParametersMultimodel gpj = om.readValue(entity, JSONParametersMultimodel.class);

      Assert.assertNotNull(gpj);

      LOG.debug("Lade Daten mit Id: {}", id);
      jwt = getJerseyClient().target(ServerTestUtils.SZENARIEN_URI + "/" + id);
      response = RESTCaller.request(jwt).delete();

      LOG.debug("Antwort: " + response.readEntity(String.class));
      Assert.assertNotEquals(Response.Status.OK.getStatusCode(), response.getStatus());

      jwt = getJerseyClient().target(ServerTestUtils.SZENARIEN_URI + "/" + id);
      response = RESTCaller.request(jwt).get();
      entity = response.readEntity(String.class);

      Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
   }

   /**
    * Test, für das hinzufügen von singel und verknüpften Modellen.
    */
   @Test
   public void testPutMultiModel() throws JsonProcessingException {
      // Test if irpact exists, if not (like on master) skip the test
      // solved this way to let loadDependencies() still crash if dependency is not found.
      try{
         ParameterBaseDependenciesUtil.getInstance().loadDependencies(3);
      } catch (NullPointerException e){
         Assume.assumeNotNull(null);
      }
      LOG.debug("uri: {}", ServerTestUtils.SZENARIEN_URI, EXAMPLEPARAMETERSETMULTI);
      Response response = RESTCaller.callPutResponse(ServerTestUtils.SZENARIEN_URI, EXAMPLEPARAMETERSETMULTI);
      Assert.assertEquals(response.getStatusInfo().getReasonPhrase(), Response.Status.OK.getStatusCode(), response.getStatus());
      JSONObject jso = new JSONObject(response.readEntity(String.class));

      final int id = (int) jso.get("id");

      final JSONObject jsa = getParameterSets();
      final JSONObject newlyAddedMetadata = jsa.getJSONObject("" + id);
      Assert.assertNotNull(newlyAddedMetadata);
      Assert.assertEquals(5, newlyAddedMetadata.getInt("modeldefinition"));

      response = RESTCaller.callGetResponse(ServerTestUtils.SZENARIEN_URI + "/" + id);
      Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

      testMultimodelOverwriting(response, id);
   }

   private void testMultimodelOverwriting(Response response, final int id) throws JsonProcessingException, JsonMappingException {
      JSONObject jso;
      JSONParametersMultimodel mmpj = Constants.MAPPER.readValue(response.readEntity(String.class), JSONParametersMultimodel.class);
      Assert.assertNotNull(mmpj);

      Response response2 = RESTCaller.callPutResponse(ServerTestUtils.SZENARIEN_URI, EXAMPLEPARAMETERSET2);
      Assert.assertEquals(Response.Status.OK.getStatusCode(), response2.getStatus());

      jso = new JSONObject(response2.readEntity(String.class));
      final int id2 = (int) jso.get("id");

      Assert.assertEquals(id + 1, id2);

      response2 = RESTCaller.callGetResponse(ServerTestUtils.SZENARIEN_URI + "/" + id2);
      JSONParametersMultimodel gpj = Constants.MAPPER.readValue(response2.readEntity(String.class), JSONParametersMultimodel.class);
      Assert.assertNotNull(gpj);
   }

   /**
    * Test, für das hinzufügen/ändern mittels ID von Modellen.
    */
   @Test
   public void testPutIDModel() {
      Response response = RESTCaller.callPutResponse(ServerTestUtils.SZENARIEN_URI, EXAMPLEPARAMETERSET);
      Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
      JSONObject jso = new JSONObject(response.readEntity(String.class));
      final int id = (int) jso.get("id");

      final JSONObject jsa = getParameterSets();
      final JSONObject newlyAddedMetadata = jsa.getJSONObject("" + id);
      Assert.assertNotNull(newlyAddedMetadata);
      Assert.assertEquals(1, newlyAddedMetadata.getInt("modeldefinition"));

      response = RESTCaller.callPutResponse(ServerTestUtils.SZENARIEN_URI + "/" + id, EXAMPLEPARAMETERSET);
      Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
   }

   /**
    * Test, für das fehlerhafte hinzufügen/ändern von Modellen mittels ID's.
    */
   @Test
   public void testPutModelWithWrongID() {
      try{
         ParameterBaseDependenciesUtil.getInstance().loadDependencies(3);
      } catch (NullPointerException e){
         Assume.assumeNotNull(null);
      }
      Response response = RESTCaller.callPutResponse(ServerTestUtils.SZENARIEN_URI, EXAMPLEPARAMETERSET);
      Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
      JSONObject jso = new JSONObject(response.readEntity(String.class));
      final int id = (int) jso.get("id");

      final JSONObject jsa = getParameterSets();
      final JSONObject newlyAddedMetadata = jsa.getJSONObject("" + id);
      Assert.assertNotNull(newlyAddedMetadata);
      Assert.assertEquals(1, newlyAddedMetadata.getInt("modeldefinition"));

      int id2 = id + 1;
      response = RESTCaller.callPutResponse(ServerTestUtils.SZENARIEN_URI + "/" + id2, EXAMPLEPARAMETERSET);
      System.out.println("RESPONSEMSG" + response.readEntity(String.class));
      Assert.assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());

      response = RESTCaller.callPutResponse(ServerTestUtils.SZENARIEN_URI + "/" + id, EXAMPLEPARAMETERSETMULTI);
      Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

      LOG.debug("uri: {}", ServerTestUtils.SZENARIEN_URI, EXAMPLEPARAMETERSETMULTI);
      response = RESTCaller.callPutResponse(ServerTestUtils.SZENARIEN_URI, EXAMPLEPARAMETERSETMULTI);
      Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
      jso = new JSONObject(response.readEntity(String.class));
   }
}
