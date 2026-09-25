package de.unileipzig.irpsim.server.data.stammdaten;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.Query;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.client.JerseyClient;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.glassfish.jersey.client.JerseyWebTarget;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManager;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManagerProxy;
import de.unileipzig.irpsim.core.standingdata.data.Stammdatum;
import de.unileipzig.irpsim.core.utils.ParameterBaseDependenciesUtil;
import de.unileipzig.irpsim.server.utils.RESTCaller;
import de.unileipzig.irpsim.server.utils.ServerTestUtils;
import de.unileipzig.irpsim.server.utils.ServerTests;
import net.javacrumbs.jsonunit.JsonAssert;

public class TestStammdatumImport extends ServerTests {

	private static final Logger LOG = LogManager.getLogger(TestStammdatumImport.class);

	private List<Integer> id;
	private int id_missingdata;

	private static final int COUNT_SCENARIOS = 2;

	@Before
	public void cleanDBUp() throws JsonProcessingException {
		StammdatenTestUtil.cleanUp();
		StammdatenTestUtil.createPrognoseszenarien();

		id = new ArrayList<>();
		id.add(StammdatenTestUtil.addStammdatum(StammdatenTestExamples.getElectricLoadExample()));
		id.add(StammdatenTestUtil.addStammdatum(StammdatenTestExamples.getThermalLoadExample()));
		id_missingdata = StammdatenTestUtil.addStammdatum(StammdatenTestExamples.getMissingDataExample());

		RESTCaller.callPutResponse(ServerTestUtils.URI + "szenariosets",
				"{\"jahr\": 2016, \"szenarien\": [{\"stelle\": 1, \"name\": \"LEME COL\"},{\"stelle\": 2, \"name\": \"LEME ECON\"}]}");

		ParameterBaseDependenciesUtil.getInstance().loadDependencies(1);
	}

	@Test
	public void testAdd() throws JsonParseException, JsonMappingException, IOException {
		final String uri = ServerTestUtils.URI + "stammdaten/" + id.get(0) + "/excel";
		LOG.debug(uri);
		final Response clientResp = sendFile(new File("src/test/resources/templates/template.xlsx"), uri);
		LOG.debug(clientResp.readEntity(String.class));
		Assert.assertEquals(Status.OK.getStatusCode(), clientResp.getStatus());

		try (final ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
			LOG.debug("Prüfe Id: {}", id);
			final Query createNativeQuery = em.createNativeQuery("SELECT COUNT(*) FROM series_data_in series, Datensatz d WHERE series.seriesid=d.id AND d.stammdatum_id=" + id.get(0));
			final BigInteger count = (BigInteger) createNativeQuery.getResultList().get(0);
			Assert.assertEquals(COUNT_SCENARIOS * 365, count.intValue());
		}

		final String response = RESTCaller.callGet(ServerTestUtils.URI + "stammdaten/" + id.get(0));

		final Stammdatum sd = new ObjectMapper().readValue(response, Stammdatum.class);

		Assert.assertEquals(100 * (2d / 12), sd.getVollstaendig().doubleValue(), 0.0001); // 12 = (1 Bezugsjahr * 2 Szenarien) + (5 Prognosejahre * 2 Szenarien)
	}
	
	@Test
	public void testAddTwice() throws JsonParseException, JsonMappingException, IOException {
		final String uri = ServerTestUtils.URI + "stammdaten/" + id.get(0) + "/excel";
		LOG.debug(uri);
		final Response clientResp = sendFile(new File("src/test/resources/templates/template.xlsx"), uri);
		LOG.debug(clientResp.readEntity(String.class));
		Assert.assertEquals(Status.OK.getStatusCode(), clientResp.getStatus());
		
		final Response clientResp2 = sendFile(new File("src/test/resources/templates/template.xlsx"), uri);
		LOG.debug(clientResp2.readEntity(String.class));
		Assert.assertEquals(Status.OK.getStatusCode(), clientResp2.getStatus());

		final String response = RESTCaller.callGet(ServerTestUtils.URI + "stammdaten/" + id.get(0));

		final Stammdatum sd = new ObjectMapper().readValue(response, Stammdatum.class);

		Assert.assertEquals(100 * (2d / 12), sd.getVollstaendig().doubleValue(), 0.0001); // 12 = (1 Bezugsjahr * 2 Szenarien) + (5 Prognosejahre * 2 Szenarien)
	}

	@Test
	public void testAddAlgebraicData() throws JsonParseException, JsonMappingException, IOException {
		final String load = "{\"szenario\":1,\"jahr\":2017,\"formel\":\"mi = min(foo) ma = max(foo) span = (ma-mi)==0? 1: (ma-mi) scaled = (foo-mi)/span result = scaled\","
				+ "\"stammdatum\":" + id.get(0) + ",\"variablenZuordnung\":{\"foo\":{\"stammdatum\":" + id.get(1) + ",\"jahr\":0}}}";

		LOG.debug("load: {}", load);
		final String uri = ServerTestUtils.URI + "stammdaten/" + id.get(0) + "/algebraicdata";
		LOG.debug("uri: {}", uri);
		final Response clientResp = RESTCaller.callPutResponse(uri, load);

		LOG.debug("clientResp: {}", clientResp.readEntity(String.class));
		Assert.assertEquals(Status.OK.getStatusCode(), clientResp.getStatus());
	}

	@Test
	public void testReverseEndpoint() throws FileNotFoundException {

		// importiere Datensätze
		final Response clientResp = sendFile(new File("src/test/resources/templates/template.xlsx"), ServerTestUtils.URI + "stammdaten/" + id.get(0) + "/excel");
		LOG.debug("clientResp: {}", clientResp.readEntity(String.class));
		Assert.assertEquals(Status.OK.getStatusCode(), clientResp.getStatus());

		final Response clientResp2 = sendFile(new File("src/test/resources/templates/template_thermal.xlsx"), ServerTestUtils.URI + "stammdaten/" + id.get(1) + "/excel");
		LOG.debug("clientResp2: {}", clientResp2.readEntity(String.class));
		Assert.assertEquals(Status.OK.getStatusCode(), clientResp2.getStatus());

		// lade den ersten Datensatz aus Stammdatum an Index 0
		final String datensatzJSON = RESTCaller.callGet(ServerTestUtils.URI + "stammdaten/" + id.get(0) + "/data");
		final JSONArray jsa = new JSONArray(datensatzJSON);
		final JSONObject object = (JSONObject) jsa.get(0);
		final int datensatzId = Integer.parseInt(object.getString("seriesid"));

		LOG.info("Datensatz: {}", datensatzId);

		// lade das zugehörige Stammdatum zu dem Datensatz und vergleiche sie
		final String datensatzLookupJSON = RESTCaller.callGet(ServerTestUtils.URI + "datensatz?id=" + datensatzId);
		JsonAssert.assertJsonEquals("[{\"stammdatum\": " + id.get(0) + ", \"datensatz\": " + datensatzId + "}]", datensatzLookupJSON);

		// lade den zweiten Datensatz aus Stammdatum an Index 1
		final String datensatzJSON2 = RESTCaller.callGet(ServerTestUtils.URI + "stammdaten/" + id.get(1) + "/data");
		LOG.debug("Anfrage: {} Datensatz2: {}", id.get(1), datensatzJSON2);
		final JSONArray jsa2 = new JSONArray(datensatzJSON2);
		final JSONObject object2 = (JSONObject) jsa2.get(0);
		final int datensatzId2 = object2.getInt("seriesid");

		final String multipleDatensatzLookupJSON = RESTCaller.callGet(ServerTestUtils.URI + "datensatz?id=" + datensatzId + "&id=" + datensatzId2);
		LOG.info("MultipleDatensatzLookupJSON: ", multipleDatensatzLookupJSON);

		final String expectedJSON = "[{\"stammdatum\": " + id.get(0) + ",\"datensatz\": " + datensatzId + "}, "
				+ "{\"stammdatum\": " + id.get(1) + ", \"datensatz\":" + datensatzId2 + "}]";
		LOG.debug("Expected: {}", expectedJSON);
		LOG.debug("Wert: {}", multipleDatensatzLookupJSON);
		JsonAssert.assertJsonEquals(expectedJSON, multipleDatensatzLookupJSON);
	}

	@Test
	public void testShortLeapYear() throws FileNotFoundException {
		final Response clientResp = sendFile(new File("src/test/resources/templates/template_shortLeapYear.xlsx"), ServerTestUtils.URI + "stammdaten/" + id.get(0) + "/excel");
		LOG.debug("clientResp: {}", clientResp.readEntity(String.class));
		Assert.assertEquals(Status.OK.getStatusCode(), clientResp.getStatus());

		try (final ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
			LOG.debug("Prüfe Id: {}", id.get(0));
			final Query createNativeQuery = em.createNativeQuery("SELECT COUNT(*) FROM series_data_in series, Datensatz d WHERE series.seriesid=d.id AND d.stammdatum_id=" + id.get(0));
			final BigInteger count = (BigInteger) createNativeQuery.getResultList().get(0);
			Assert.assertEquals(COUNT_SCENARIOS * 365, count.intValue());

			final DateTime date = new DateTime(2016, 2, 28, 23, 59);
			LOG.debug("date: {}",date);

			final Query before1marchQuery = em.createNativeQuery("SELECT COUNT(*) FROM series_data_in series, Datensatz d WHERE series.seriesid=d.id AND d.stammdatum_id=" + id.get(0)
					+ " AND series.unixtimestamp < " + date.getMillis());
			final BigInteger before1march = (BigInteger) before1marchQuery.getResultList().get(0);
			Assert.assertEquals(COUNT_SCENARIOS * 59, before1march.intValue());

		}
	}

	@Test
	public void testAddWithParent() throws FileNotFoundException {
		final Response clientResp = sendFile(new File("src/test/resources/templates/template.xlsx"), ServerTestUtils.URI + "stammdaten/excel");
		LOG.debug("clientResp: {}", clientResp.readEntity(String.class));
		Assert.assertEquals(Status.OK.getStatusCode(), clientResp.getStatus());

		try (final ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
			final Query createNativeQuery = em.createNativeQuery("SELECT COUNT(*) FROM series_data_in series, Datensatz d WHERE series.seriesid=d.id AND d.stammdatum_id=" + id.get(0));
			final BigInteger count = (BigInteger) createNativeQuery.getResultList().get(0);
			Assert.assertEquals(COUNT_SCENARIOS * 365, count.intValue());
		}
	}

	@Test
	public void testIncompleteTimeseries() throws FileNotFoundException {
		final Response clientResp = sendFile(new File("src/test/resources/templates/template_incomplete.xlsx"), ServerTestUtils.URI + "stammdaten/" + id.get(0) + "/excel");
		Assert.assertEquals(Status.BAD_REQUEST.getStatusCode(), clientResp.getStatus());
	}

	@Test
	public void testWrongDataStammdatum() throws FileNotFoundException {
		final Response clientResp = sendFile(new File("src/test/resources/templates/template_wrong.xlsx"), ServerTestUtils.URI + "stammdaten/" + id.get(0) + "/excel");
		Assert.assertEquals(Status.BAD_REQUEST.getStatusCode(), clientResp.getStatus());
	}

	@Test
	public void testSendEmpty() throws IOException {
		final JerseyClient jc = new JerseyClientBuilder().register(MultiPartFeature.class).build();
		final JerseyWebTarget target = jc.target(ServerTestUtils.URI + "stammdaten/" + id.get(0) + "/excel");
		final Response response = RESTCaller.request(target).get();

		final InputStream input = (InputStream) response.getEntity();

		final byte[] SWFByteArray = IOUtils.toByteArray(input);

		final File targetFile = new File("target/result.xlsx");

		final FileOutputStream fos = new FileOutputStream(targetFile);
		fos.write(SWFByteArray);
		fos.flush();
		fos.close();

		final Response clientResp = sendFile(targetFile, ServerTestUtils.URI + "stammdaten/" + id.get(0) + "/excel");
		LOG.debug("clientResp: {}", clientResp.readEntity(String.class));
		Assert.assertEquals(Status.OK.getStatusCode(), clientResp.getStatus());
	}

	@Test
	public void testMissingData() throws IOException {
		final JerseyClient jc = new JerseyClientBuilder().register(MultiPartFeature.class).build();
		final JerseyWebTarget target = jc.target(ServerTestUtils.URI + "stammdaten/" + id_missingdata + "/excel");
		final Response response = RESTCaller.request(target).get();

		Assert.assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());
	}

	static Response sendFile(final File inFile, final String endpoint) {
		final JerseyClient jc = new JerseyClientBuilder().register(MultiPartFeature.class).build();
		final JerseyWebTarget target = jc.target(endpoint);

		final FormDataMultiPart multiPart = new FormDataMultiPart();
		if (inFile != null) {
			multiPart.bodyPart(new FileDataBodyPart("file", inFile, MediaType.APPLICATION_OCTET_STREAM_TYPE));
		}

		final Response clientResp = RESTCaller.request(target, MediaType.MULTIPART_FORM_DATA_TYPE).put(Entity.entity(multiPart, MediaType.MULTIPART_FORM_DATA));
		return clientResp;
	}

}
