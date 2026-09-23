package de.unileipzig.irpsim.server.optimisation.endpoints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unileipzig.irpsim.core.simulation.data.json.IntermediarySimulationStatus;
import de.unileipzig.irpsim.core.simulation.data.json.JSONParametersMultimodel;
import de.unileipzig.irpsim.core.simulation.data.persistence.State;
import de.unileipzig.irpsim.core.testutils.DatabaseTestUtils;
import de.unileipzig.irpsim.core.utils.TestFiles;
import de.unileipzig.irpsim.server.optimisation.comparators.modelDifferences.ModelDifference;
import de.unileipzig.irpsim.server.utils.RESTCaller;
import de.unileipzig.irpsim.server.utils.ServerTestUtils;
import de.unileipzig.irpsim.server.utils.ServerTests;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.client.JerseyWebTarget;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

public class ModelDiffEndpointTest extends ServerTests {


   private static final Logger LOG = LogManager.getLogger(ModelDiffEndpointTest.class);
   private final ObjectMapper om = new ObjectMapper();

   @Before
   public void setUp() {

   }

   @Test
   public void endpointPut() throws IOException, InterruptedException {
      final JSONParametersMultimodel gpj = DatabaseTestUtils.getParameterObject(TestFiles.DAYS_3.make());

      ServerTestUtils.getInstance();
      final long jobid = ServerTestUtils.startSimulation(om.writeValueAsString(gpj));
      final String testURI = ServerTestUtils.OPTIMISATION_URI + "/" + jobid + "/";
      final JerseyWebTarget jwt = getJerseyClient().target(testURI);
      String resultstring = RESTCaller.request(jwt).accept(MediaType.APPLICATION_JSON).get().readEntity(String.class);

      waitUntilJobIsDone(jwt, resultstring);

      final JSONParametersMultimodel gpj2 = DatabaseTestUtils.getParameterObject(TestFiles.DAYS_21.make());

      final long jobid2 = ServerTestUtils.startSimulation(om.writeValueAsString(gpj2));
      final String testURI2 = ServerTestUtils.OPTIMISATION_URI + "/" + jobid2 + "/";
      final JerseyWebTarget jwt2 = getJerseyClient().target(testURI2);
      String resultstring2 = RESTCaller.request(jwt2).accept(MediaType.APPLICATION_JSON).get().readEntity(String.class);

      waitUntilJobIsDone(jwt2,resultstring2);

      //Both jobs are done
      List<Long> jobs = new LinkedList<>();
      jobs.add(jobid);
      jobs.add(jobid2);
      String jobString = om.writeValueAsString(jobs);
      String uri = ServerTestUtils.URI + "modeldifference/jobIds";
      Response response = RESTCaller.callPutResponse(uri, jobString);

      Assert.assertEquals(200, response.getStatus());
      Assert.assertEquals(MediaType.APPLICATION_JSON_TYPE.withCharset("UTF-8"), response.getMediaType());

      final InputStream input = (InputStream) response.getEntity();
      String body = IOUtils.toString(input, "UTF-8");
      List<ModelDifference> differenceList = om.readValue(body, new TypeReference<List<ModelDifference>>(){});

      assertNotNull(differenceList);
      assertFalse(differenceList.isEmpty());
      assertNotNull(differenceList.get(0));
      ModelDifference diffValues = differenceList.get(0);
      assertTrue(diffValues.leftMap.isEmpty());
      assertTrue(diffValues.rightMap.isEmpty());
      assertFalse(diffValues.differingMap.isEmpty());
   }

   private void waitUntilJobIsDone(JerseyWebTarget jwt, String resultstring) throws JsonProcessingException, InterruptedException {
      IntermediarySimulationStatus iss = om.readValue(resultstring, IntermediarySimulationStatus.class);
      State state = iss.getState();
      while (state != State.FINISHED && state != State.ERROR && state != State.FINISHEDERROR) {

         resultstring = RESTCaller.request(jwt).accept(MediaType.APPLICATION_JSON).get().readEntity(String.class);
         iss = om.readValue(resultstring, IntermediarySimulationStatus.class);
         state = iss.getState();
         LOG.info("Jobs ("+iss.getId()+") State: " + iss.getState());
         Thread.sleep(1000);
      }
   }
}
