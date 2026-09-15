package de.unileipzig.irpsim.server.performance;

import javax.ws.rs.core.Response;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import de.unileipzig.irpsim.core.utils.TestFiles;
import de.unileipzig.irpsim.server.data.simulationparameters.ScenarioEndpoint;
import de.unileipzig.irpsim.server.marker.DBTest;
import de.unileipzig.irpsim.server.marker.PerformanceTest;
import de.unileipzig.irpsim.server.marker.RESTTest;
import de.unileipzig.irpsim.server.utils.RESTCaller;
import de.unileipzig.irpsim.server.utils.ServerTestUtils;
import de.unileipzig.irpsim.server.utils.ServerTests;

/**
 * @author krauss
 */
@Category({ DBTest.class, RESTTest.class, PerformanceTest.class })
//@RunWith(PerformanceTestRunnerJUnit.class)
public final class ParametersetReferencesCreationPerformanceTest extends ServerTests {

	private static final String PARAMETERSET = ServerTestUtils.getParameterset(TestFiles.TEST.make());

	/**
	 *
	 */
	@Test
//	@PerformanceTest(executionTimes = 4, warmupExecutions = 2, timeout = 300000)
	public void testPuttingParameterSet() {
		final Response response = RESTCaller.callPutResponse(ServerTestUtils.SZENARIEN_URI, PARAMETERSET);
		Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
	}

	/**
	 *
	 */
	@Test
//	@PerformanceTest(executionTimes = 4, warmupExecutions = 2, timeout = 30000)
	public void testDirectPuttingParameterSet() {
		// Ohne Sicherheitskontext wird kein Eigentuemer eingetragen; der Test prueft
		// weiterhin ausschliesslich das Anlegen des Parametersatzes.
		final Response response = new ScenarioEndpoint().createNewSimulationParameters(PARAMETERSET, null);
		Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
	}
}
