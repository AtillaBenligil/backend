package de.unileipzig.irpsim.server.data.stammdaten;

import java.io.IOException;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.unileipzig.irpsim.core.standingdata.data.Stammdatum;
import de.unileipzig.irpsim.server.utils.RESTCaller;
import de.unileipzig.irpsim.server.utils.ServerTestUtils;
import de.unileipzig.irpsim.server.utils.ServerTests;
import net.javacrumbs.jsonunit.JsonMatchers;
import net.javacrumbs.jsonunit.core.Option;

public class TestStammdaten extends ServerTests {

	private static final Logger LOG = LogManager.getLogger(TestStammdaten.class);

	private static final ObjectMapper mapper = new ObjectMapper();

	@Before
	public void cleanDBUp() {
		StammdatenTestUtil.cleanUp();
		StammdatenTestUtil.createPrognoseszenarien();
	}

	@Test
	public void testAddAndGet() throws IOException {
		final int id = StammdatenTestUtil.addStammdatum(StammdatenTestExamples.getThermalLoadExample());

		final Response getResponse = RESTCaller.callGetResponse(ServerTestUtils.URI + "stammdaten/");
		final String returnString = getResponse.readEntity(String.class);

		LOG.debug(returnString);

		MatcherAssert.assertThat(returnString, Matchers.containsString("" + id));

		final Response getConcreteResponse = RESTCaller.callGetResponse(ServerTestUtils.URI + "stammdaten/" + id);
		final String concreteResponse = getConcreteResponse.readEntity(String.class);
		LOG.debug(concreteResponse);
		final Stammdatum sdNew = mapper.readValue(concreteResponse, Stammdatum.class);
		sdNew.setId(0);
		final String addJSON = mapper.writeValueAsString(StammdatenTestExamples.getThermalLoadExample());
		Assert.assertEquals(addJSON, mapper.writeValueAsString(sdNew));
	}

	@Test
	public void testDoubleAdd() throws IOException {

		@SuppressWarnings("unused")
	   final int id = StammdatenTestUtil.addStammdatum(StammdatenTestExamples.getThermalLoadExample());

		final Response getResponse = RESTCaller.callGetResponse(ServerTestUtils.URI + "stammdaten/");
		final String returnString = getResponse.readEntity(String.class);

		LOG.debug(returnString);

		final String jsonString = mapper.writeValueAsString(StammdatenTestExamples.getThermalLoadExample());
		final Response putResponse = RESTCaller.callPutResponse(ServerTestUtils.URI + "stammdaten/", jsonString);

		MatcherAssert.assertThat(putResponse.readEntity(String.class), Matchers.containsString("Stammdatum mit gleichem"));
	}

	@Test
	public void testQuery() throws IOException {
		final Stammdatum loadExample = StammdatenTestExamples.getLoadExample();
		final int id_load = StammdatenTestUtil.addStammdatum(loadExample);
		final Stammdatum thermalLoadExample = StammdatenTestExamples.getThermalLoadExample();
		thermalLoadExample.setReferenz(loadExample);
		final int id_therm = StammdatenTestUtil.addStammdatum(thermalLoadExample);
		final int id_el = StammdatenTestUtil.addStammdatum(StammdatenTestExamples.getElectricLoadExample());

		final Response getTypResponse = RESTCaller.callGetResponse(ServerTestUtils.URI + "stammdaten?typ=par_L");
		final String typString = getTypResponse.readEntity(String.class);
		MatcherAssert.assertThat(typString, JsonMatchers.jsonEquals("[" + id_therm + "," + id_el + "," + id_load + "]").when(Option.IGNORING_ARRAY_ORDER));

		final Response getTyp2Response = RESTCaller.callGetResponse(ServerTestUtils.URI + "stammdaten?typ=par_L_DS_G");
		final String typ2String = getTyp2Response.readEntity(String.class);
		MatcherAssert.assertThat(typ2String, JsonMatchers.jsonEquals("[" + id_therm + "]"));

		final Response getBezugsjahrResponse = RESTCaller.callGetResponse(ServerTestUtils.URI + "stammdaten?bezugsjahr=2016");
		final String bezugsjahrString = getBezugsjahrResponse.readEntity(String.class);
		MatcherAssert.assertThat(bezugsjahrString, JsonMatchers.jsonEquals("[" + id_therm + "," + id_el + "," + id_load + "]").when(Option.IGNORING_ARRAY_ORDER));

		final Response getBezugsjahr2Response = RESTCaller.callGetResponse(ServerTestUtils.URI + "stammdaten?bezugsjahr=2015");
		final String bezugsjahr2String = getBezugsjahr2Response.readEntity(String.class);
		MatcherAssert.assertThat(bezugsjahr2String, JsonMatchers.jsonEquals("[]"));

		final Response getReferenzResponse = RESTCaller.callGetResponse(ServerTestUtils.URI + "stammdaten?referenz=" + id_load);
		final String referenzString = getReferenzResponse.readEntity(String.class);
		MatcherAssert.assertThat(referenzString, JsonMatchers.jsonEquals("[" + id_therm + "]"));

	}

	@Test
	public void testAddAndChange() throws IOException {
		final Stammdatum thermalLoad = StammdatenTestExamples.getThermalLoadExample();
		final String jsonInput = mapper.writeValueAsString(thermalLoad);
		final Response putResponse = RESTCaller.callPutResponse(ServerTestUtils.URI + "stammdaten/", jsonInput);
		final int id = StammdatenTestUtil.getId(putResponse);

		final Response getResponse = RESTCaller.callGetResponse(ServerTestUtils.URI + "stammdaten/");
		final String returnString = getResponse.readEntity(String.class);

		LOG.debug(returnString);

		MatcherAssert.assertThat(returnString, Matchers.containsString("" + id));

		thermalLoad.setTyp("par_F_SMS_E");
		thermalLoad.setStandardszenario(true);

		final String jsonInputChanged = mapper.writeValueAsString(thermalLoad);
		final Response putResponseChanged = RESTCaller.callPutResponse(ServerTestUtils.URI + "stammdaten/" + id, jsonInputChanged);
		LOG.debug(putResponseChanged.readEntity(String.class));

		final Response getResponseChanged = RESTCaller.callGetResponse(ServerTestUtils.URI + "stammdaten/" + id);
		final String returnStringChanged = getResponseChanged.readEntity(String.class);

		// Die verantwortlichen Personen sind durch die Rechteverwaltung ersetzt und
		// erscheinen nicht mehr in der Schnittstelle.
		MatcherAssert.assertThat(returnStringChanged, Matchers.not(Matchers.containsString("verantwortlicher")));
		MatcherAssert.assertThat(returnStringChanged, JsonMatchers.jsonPartEquals("typ", "par_F_SMS_E"));
		MatcherAssert.assertThat(returnStringChanged, JsonMatchers.jsonPartEquals("standardszenario", "true"));
	}

	@Test
	public void testRemove() throws JsonProcessingException {
		final String jsonInput = mapper.writeValueAsString(StammdatenTestExamples.getThermalLoadExample());

		final Response putResponse = RESTCaller.callPutResponse(ServerTestUtils.URI + "stammdaten/", jsonInput);
		final int id = StammdatenTestUtil.getId(putResponse);

		final Response getResponse = RESTCaller.callGetResponse(ServerTestUtils.URI + "stammdaten/");
		final String returnString = getResponse.readEntity(String.class);

		LOG.debug(returnString);
		MatcherAssert.assertThat(returnString, Matchers.containsString("" + id));

		final Response getConcreteResponse = RESTCaller.callGetResponse(ServerTestUtils.URI + "stammdaten/" + id);
		final String concreteResponse = getConcreteResponse.readEntity(String.class);

		LOG.debug(concreteResponse);

		final Response deleteResponse = RESTCaller.callDeleteResponse(ServerTestUtils.URI + "stammdaten/" + id);
		Assert.assertEquals(Status.OK.getStatusCode(), deleteResponse.getStatus());
	}
}
