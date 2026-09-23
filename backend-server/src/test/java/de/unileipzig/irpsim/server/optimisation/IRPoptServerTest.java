package de.unileipzig.irpsim.server.optimisation;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.client.JerseyWebTarget;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.joda.time.DateTime;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import de.unileipzig.irpsim.core.Constants;
import de.unileipzig.irpsim.core.data.timeseries.LoadElement;
import de.unileipzig.irpsim.core.simulation.data.json.IntermediarySimulationStatus;
import de.unileipzig.irpsim.core.simulation.data.json.JSONParametersMultimodel;
import de.unileipzig.irpsim.core.simulation.data.json.YearData;
import de.unileipzig.irpsim.core.simulation.data.persistence.OptimisationJobPersistent;
import de.unileipzig.irpsim.core.simulation.data.persistence.State;
import de.unileipzig.irpsim.core.standingdata.DataLoader;
import de.unileipzig.irpsim.core.testutils.DatabaseTestUtils;
import de.unileipzig.irpsim.core.utils.TestFiles;
import de.unileipzig.irpsim.server.optimisation.queue.OptimisationJobHandler;
import de.unileipzig.irpsim.server.utils.RESTCaller;
import de.unileipzig.irpsim.server.utils.ServerTestUtils;
import de.unileipzig.irpsim.server.utils.ServerTests;

/**
 * Testet, ob Anfragen an das Basismodell über den Server korrekt funktionieren.
 *
 * @author reichelt
 */

public final class IRPoptServerTest extends ServerTests {
   private static final Logger LOG = LogManager.getLogger(IRPoptServerTest.class);

   @Rule
   public TestName name = new TestName();

   /**
    * Gibt den Namen des Tests aus, um besser debuggen zu können.
    */
   @Before
   public void echoName() {
      LOG.info(name.getMethodName());
   }

   /**
    *
    */
   @Test
   public void testSimulationStatus() throws JsonParseException, JsonMappingException, IOException {
      final String content = DatabaseTestUtils.getParameterText(TestFiles.DAYS_3.make());
      ServerTestUtils.getInstance();
      final long jobID = ServerTestUtils.startSimulation(content);
      final String resultstring = ServerTestUtils.waitForSimulationEnd(jobID);
      LOG.info("GET: " + resultstring + " " + resultstring.getClass());

      final IntermediarySimulationStatus iss = Constants.MAPPER.readValue(resultstring, IntermediarySimulationStatus.class);

      MatcherAssert.assertThat(iss.getFinishedsteps(), Matchers.equalTo(iss.getSimulationsteps()));
   }

   /**
    * Testet ob das Basismodell beendet wird, wenn man eine DELETE sendet.
    * 
    * @throws IOException
    * @throws JsonMappingException
    * @throws JsonParseException
    */
   @Test
   public void testStop() throws InterruptedException, JsonParseException, JsonMappingException, IOException {
      final String content = DatabaseTestUtils.getParameterText(TestFiles.DAYS_21.make());

      /*
       * Test failed non-deterministic, so it is executed not only once.
       */
      for (int i = 0; i < 20; i++) {
         ServerTestUtils.getInstance();
         final long jobid = ServerTestUtils.startSimulation(content);
         Thread.sleep(1500);
         ServerTestUtils.getInstance();
         ServerTestUtils.stopJob(jobid);
         Thread.sleep(1000);
         final String testURI = ServerTestUtils.OPTIMISATION_URI + "/" + jobid + "/";
         LOG.info("Status-URI: " + testURI);
         final JerseyWebTarget jwt = getJerseyClient().target(testURI);

         final Response response = RESTCaller.request(jwt).accept(MediaType.APPLICATION_JSON).get();
         final String resultstring = response.readEntity(String.class);
         LOG.info("GET: " + resultstring + " " + resultstring.getClass());

         final IntermediarySimulationStatus status = Constants.MAPPER.readValue(resultstring, IntermediarySimulationStatus.class);

         MatcherAssert.assertThat(status.getFinishedsteps(), Matchers.lessThan(status.getSimulationsteps()));
         Assert.assertEquals("Job " + jobid + " (" + i + ") has wrong state", State.ABORTED, status.getState());
      }

   }

   @Test
   public void testNotExistingFailure() throws InterruptedException, JsonParseException, JsonMappingException, IOException {
      final String content = DatabaseTestUtils.getParameterText(TestFiles.DAYS_3.make());

      final JSONParametersMultimodel parameters = Constants.MAPPER.readValue(content, JSONParametersMultimodel.class);
      final YearData yearData = parameters.getModels().get(0).getYears().get(0);
      final String missingname = yearData.getTimeseries().keySet().iterator().next();
      LOG.debug("Entferne: {}", missingname);
      yearData.getTimeseries().remove(missingname);
      final long jobID = ServerTestUtils.startSimulation(Constants.MAPPER.writeValueAsString(parameters));
      final String resultstring = ServerTestUtils.waitForSimulationEnd(jobID);
      MatcherAssert.assertThat(resultstring, Matchers.containsString("messages"));
      MatcherAssert.assertThat(resultstring, Matchers.containsString("GAMS-Fehler"));
   }

   @Test
   public void testTooShortFailure() throws InterruptedException, JsonParseException, JsonMappingException, IOException {
      final String content = DatabaseTestUtils.getParameterText(TestFiles.DAYS_3.make());

      final JSONParametersMultimodel parameters = Constants.MAPPER.readValue(content, JSONParametersMultimodel.class);
      final YearData yearData = parameters.getModels().get(0).getYears().get(0);
      final String missingname = yearData.getTimeseries().keySet().iterator().next();
      LOG.debug("Kürze: {}", missingname);
      final List<?> vals = (List<?>) yearData.getTimeseries().get(missingname);
      vals.remove(1);
      final long jobID = ServerTestUtils.startSimulation(Constants.MAPPER.writeValueAsString(parameters));
      final String resultstring = ServerTestUtils.waitForSimulationEnd(jobID);
      MatcherAssert.assertThat(resultstring, Matchers.containsString("messages"));
      MatcherAssert.assertThat(resultstring, Matchers.containsString("ist zu kurz"));
   }

   @Test
   public void testResults() throws InterruptedException, JsonParseException, JsonMappingException, IOException {
      final String content = DatabaseTestUtils.getParameterText(TestFiles.TEST.make());
      ServerTestUtils.getInstance();
      final long jobid = ServerTestUtils.startSimulation(content);

      checkJsonParametersNotNull(jobid);

      ServerTestUtils.waitForSimulationEnd(jobid);
      String testURI = ServerTestUtils.OPTIMISATION_URI + "/" + jobid + "/";
      String resultstring = RESTCaller.callGet(testURI);
      LOG.info("GET: " + resultstring + " " + resultstring.getClass());
      final JSONObject jso = new JSONObject(resultstring);

      final int steps = (int) jso.get("finishedsteps");
      final int allSteps = (int) jso.get("simulationsteps");

      Assert.assertEquals(allSteps, steps);

      testURI = ServerTestUtils.OPTIMISATION_URI + "/" + jobid + "/results";
      LOG.info("Results-URI: " + testURI);

      resultstring = RESTCaller.callGet(testURI);

      LOG.warn(resultstring);

      final JSONParametersMultimodel result = Constants.MAPPER.readValue(resultstring, JSONParametersMultimodel.class);
      Assert.assertNotNull(result);

      final YearData year0 = result.getModels().get(0).getYears().get(0);
      Assert.assertNotNull(result);

      LOG.debug(year0.getTimeseries().keySet());

      final String timeseries = (String) year0.getTimeseries().get("par_out_IuO_ESector_Orga");

      final int timeseriesId = Integer.parseInt(timeseries);
      final DataLoader dl = new DataLoader(Arrays.asList(new Integer[] { timeseriesId }));
      final List<LoadElement> load = dl.getResultData().get(timeseriesId);

      Assert.assertEquals(672, load.size());

      final DataLoader dl2 = new DataLoader(Arrays.asList(new Integer[] { timeseriesId }), new DateTime(2015, 1, 1, 0, 0), new DateTime(2015, 8, 1, 0, 0), 672);
      final List<LoadElement> load2 = dl2.getResultData().get(timeseriesId);
      MatcherAssert.assertThat(load2.size(), Matchers.greaterThan(0));
   }

   private void checkJsonParametersNotNull(final long jobid) {
      final OptimisationJobPersistent persistentJob = OptimisationJobHandler.getInstance().getJob(jobid);
      String jsonParameterString = persistentJob.getJsonParameter();
      Assert.assertNotNull(jsonParameterString);
   }
}
