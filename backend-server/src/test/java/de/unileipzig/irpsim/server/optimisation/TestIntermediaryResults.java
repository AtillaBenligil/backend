package de.unileipzig.irpsim.server.optimisation;

import java.io.FileNotFoundException;
import java.util.Scanner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.client.JerseyWebTarget;
import org.json.JSONArray;
import org.junit.Test;

import de.unileipzig.irpsim.core.utils.TestFiles;
import de.unileipzig.irpsim.server.utils.ServerTestUtils;
import de.unileipzig.irpsim.server.utils.RESTCaller;
import de.unileipzig.irpsim.server.utils.ServerTests;

/**
 * Testet, ob der Server erfolgreich Zwischenergebnisse zurückliefert.
 *
 * @author reichelt
 */
public final class TestIntermediaryResults extends ServerTests {

	private static final Logger LOG = LogManager.getLogger(TestIntermediaryResults.class);

	/**
	 *
	 */
	@Test
	public void testSimulationIndermediaryResults() throws FileNotFoundException, InterruptedException {
		final Scanner scanner = new Scanner(TestFiles.TEST.make());
		final String content = scanner.useDelimiter("\\Z").next();
		scanner.close();
		LOG.trace("Anfrage: " + content);

		JerseyWebTarget jwt = jc.target(ServerTestUtils.OPTIMISATION_URI + "?type=Basismodell&onlystart=true");
		Response response = RESTCaller.request(jwt).accept(MediaType.APPLICATION_JSON).post(Entity.entity(content, MediaType.APPLICATION_JSON), Response.class);
		final String startstring = response.readEntity(String.class);
		LOG.debug("Start: {}", startstring);
		final JSONArray jsa = new JSONArray(startstring);

		Thread.sleep(500);

		final String uri = ServerTestUtils.OPTIMISATION_URI + "/" + jsa.getInt(0) + "/results";
		LOG.info("Anfrage-URI:" + uri);
		jwt = jc.target(uri);
		response = RESTCaller.request(jwt).accept(MediaType.APPLICATION_JSON).get();
		final String resultstring = response.readEntity(String.class);
		// logger.debug("GET: " + resultstring + " " + resultstring.getClass());

		// ObjectMapper om = new ObjectMapper();
		// try {
		LOG.debug(resultstring);
		// JSONObject jso2 = new JSONObject(resultstring);
		// GAMSParametersJSON gp = om.readValue(resultstring,
		// GAMSParametersJSON.class);
		// } catch (IOException e) {
		// Assert.fail("Ergebnis entspricht nicht dem vorgegebenen
		// JSON-Schema");
		// e.printStackTrace();
		// }

	}
}
