package de.unileipzig.irpsim.server.optimisation;

import java.io.IOException;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.client.JerseyWebTarget;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import de.unileipzig.irpsim.core.testutils.DatabaseTestUtils;
import de.unileipzig.irpsim.core.utils.TestFiles;
import de.unileipzig.irpsim.server.utils.ServerTestUtils;
import de.unileipzig.irpsim.server.utils.RESTCaller;
import de.unileipzig.irpsim.server.utils.ServerTests;

/**
 * Testet die Dateiausgabefunktionalitäten Anhand des Basismodells.
 *
 * @author reichelt
 */
public final class OptimisationFileTest extends ServerTests {

	private static final Logger LOG = LogManager.getLogger(OptimisationFileTest.class);

	@Rule
	public TestName name = new TestName();

	/**
	 *
	 */
	@Test
	public void testBasicRequest() throws JsonParseException, JsonMappingException, IOException, InterruptedException {
		final String content = DatabaseTestUtils.getParameterText(TestFiles.TEST.make());
		final long jobid = ServerTestUtils.startSimulation(content);

		LOG.info("ID: {}", jobid);

		ServerTestUtils.waitForSimulationEnd(jobid);

		Thread.sleep(1000);

		final JerseyWebTarget jwt = getJerseyClient().target(ServerTestUtils.OPTIMISATION_URI + "/" + jobid + "/0/lstfile");
		final Response response = RESTCaller.request(jwt).get();

		final String responseString = response.readEntity(String.class);

		LOG.trace("Response: " + responseString);
		Assert.assertNotNull(responseString);
		// Assert.assertThat(responseString, Matchers.containsString("GAMS"));
	}
}
