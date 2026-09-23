package de.unileipzig.irpsim.server.optimisation;

import static org.junit.Assert.fail;

import java.io.IOException;

import javax.ws.rs.core.MediaType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.client.JerseyWebTarget;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.unileipzig.irpsim.core.simulation.data.json.IntermediarySimulationStatus;
import de.unileipzig.irpsim.core.simulation.data.json.JSONParametersMultimodel;
import de.unileipzig.irpsim.core.simulation.data.json.UserDefinedDescription;
import de.unileipzig.irpsim.core.simulation.data.persistence.State;
import de.unileipzig.irpsim.core.testutils.DatabaseTestUtils;
import de.unileipzig.irpsim.core.utils.TestFiles;
import de.unileipzig.irpsim.server.utils.ServerTestUtils;
import de.unileipzig.irpsim.server.utils.RESTCaller;
import de.unileipzig.irpsim.server.utils.ServerTests;

/**
 * Testet die Statusmeldungen mit Basismodell.
 *
 * @author reichelt
 */
public final class JobStateTest extends ServerTests {

	private static final Logger LOG = LogManager.getLogger(JobStateTest.class);
	private final ObjectMapper om = new ObjectMapper();

	/**
	 * Testet ob sich der Status immer in der intern richtigen Ordnung ändert.
	 */
	@Test
	public void testSimulationStateName() throws JsonParseException, JsonMappingException, IOException, InterruptedException {
		final JSONParametersMultimodel gpj = DatabaseTestUtils.getParameterObject(TestFiles.DAYS_3.make());

		ServerTestUtils.getInstance();
		final long jobid = ServerTestUtils.startSimulation(om.writeValueAsString(gpj));
		final String testURI = ServerTestUtils.OPTIMISATION_URI + "/" + jobid + "/";
		final JerseyWebTarget jwt = getJerseyClient().target(testURI);
		String resultstring = RESTCaller.request(jwt).accept(MediaType.APPLICATION_JSON).get().readEntity(String.class);

		IntermediarySimulationStatus iss = om.readValue(resultstring, IntermediarySimulationStatus.class);
		State state = iss.getState();
		State previousState = state;
		int previousYear = iss.getYearIndex();
		while (state != State.FINISHED && state != State.ERROR && state != State.FINISHEDERROR) {

			resultstring = RESTCaller.request(jwt).accept(MediaType.APPLICATION_JSON).get().readEntity(String.class);
			iss = om.readValue(resultstring, IntermediarySimulationStatus.class);
			final UserDefinedDescription description = iss.getDescription();
			assertEqualDescription(description);
			state = iss.getState();
			// This happens only during Jenkins Builds
			if (previousYear == iss.getYearIndex()) {
				checkState(state, previousState, iss.getYearIndex());
			}
			previousState = state;
			previousYear = iss.getYearIndex();
			LOG.debug("State: {} Vorhergehender Status: {}", state, previousState);
			Thread.sleep(100);
		}
		Thread.sleep(5000);
		final String finishedString = RESTCaller.request(jwt).accept(MediaType.APPLICATION_JSON).get().readEntity(String.class);
		final IntermediarySimulationStatus statePersisted = om.readValue(finishedString, IntermediarySimulationStatus.class);

		// if previousState == Error the state will be set to FINISHEDERROR within ms but not noticed to the state variable
		boolean stateChangeOnError = !(state == State.ERROR && statePersisted.getState() == State.FINISHEDERROR);
		if (state != statePersisted.getState() && stateChangeOnError) {
			fail("Statusänderung nach 5 Sekunden nach Ende!");
		}
		final UserDefinedDescription description = statePersisted.getDescription();
		assertEqualDescription(description);
		Assert.assertEquals("2015", description.getSupportiveYears());

		Thread.sleep(5000);
	}

	/**
	 * Testet ob sich der Status immer in der intern richtigen Ordnung ändert.
	 */
	@Test
	public void testMultipleYearSimulationStateName() throws JsonParseException, JsonMappingException, IOException, InterruptedException {
		final JSONParametersMultimodel gpj = DatabaseTestUtils.getParameterObject(TestFiles.MULTIPLE_YEAR.make());

		ServerTestUtils.getInstance();
		final long jobid = ServerTestUtils.startSimulation(om.writeValueAsString(gpj));
		final String testURI = ServerTestUtils.OPTIMISATION_URI + "/" + jobid + "/";
		final JerseyWebTarget jwt = getJerseyClient().target(testURI);
		String resultstring = RESTCaller.request(jwt).accept(MediaType.APPLICATION_JSON).get().readEntity(String.class);

		IntermediarySimulationStatus iss = om.readValue(resultstring, IntermediarySimulationStatus.class);
		State state = iss.getState();
		State previousState = state;
		int previousYear = iss.getYearIndex();
		while (state != State.FINISHED && state != State.ERROR && state != State.FINISHEDERROR) {
			resultstring = RESTCaller.request(jwt).accept(MediaType.APPLICATION_JSON).get().readEntity(String.class);
			iss = om.readValue(resultstring, IntermediarySimulationStatus.class);
			final UserDefinedDescription description = iss.getDescription();
			assertEqualDescription(description);
			state = iss.getState();
			if (previousYear == iss.getYearIndex()) {
				checkState(state, previousState, iss.getYearIndex());
			}
			previousState = state;
			previousYear = iss.getYearIndex();
			LOG.debug("State: {} Vorhergehender Status: {}", state, previousState);
		}
		Thread.sleep(5000);
		final String finishedString = RESTCaller.request(jwt).accept(MediaType.APPLICATION_JSON).get().readEntity(String.class);
		final IntermediarySimulationStatus statePersisted = om.readValue(finishedString, IntermediarySimulationStatus.class);

		LOG.debug("statePersisted: {}", statePersisted);

		// if previousState == Error the state will be set to FINISHEDERROR within ms but not noticed to the state variable
		boolean stateChangeOnError = !(state == State.ERROR && statePersisted.getState() == State.FINISHEDERROR);
		if (state != statePersisted.getState() && stateChangeOnError) {
			fail("Statusänderung nach 5 Sekunden nach Ende! Vorher: " + state + " Jetzt: " + statePersisted.getState());
		}
		final UserDefinedDescription description = statePersisted.getDescription();
		assertEqualDescription(description);
		Assert.assertEquals("2015;2015", description.getSupportiveYears());
	}

	public void checkState(final State state, final State previousState, final int year) throws InterruptedException {
		Thread.sleep(10);
		switch (state) {
		case WAITING:
			if (previousState != State.WAITING) {
				fail("Jahr " + year + " NOTSTARTED folgt auf späteren Status: " + previousState);
			} else {
				break;
			}
		case LOADING:
			if (previousState != State.WAITING && previousState != State.LOADING) {
				fail("Jahr " + year + " LOADING folgt auf späteren Status: " + previousState);
			} else {
				break;
			}
		case PARAMETERIZING:
			if (previousState != State.WAITING && previousState != State.LOADING && previousState != State.PARAMETERIZING) {
				fail("Jahr " + year + " PARAMETERIZING folgt auf späteren Status: " + previousState);
			} else {
				break;
			}
		case RUNNING:
			if (previousState != State.WAITING && previousState != State.LOADING && previousState != State.PARAMETERIZING && previousState != State.RUNNING) {
				fail("Jahr " + year + " RUNNING folgt auf späteren Status: " + previousState);
			} else {
				break;
			}
		case PERSISTING:
			if (previousState != State.WAITING && previousState != State.LOADING && previousState != State.PARAMETERIZING && previousState != State.RUNNING
					&& previousState != State.PERSISTING) {
				fail("Jahr " + year + " PERSISTING folgt auf späteren Status: " + previousState);
			} else {
				break;
			}
		case INTERPOLATING:
			if (previousState != State.WAITING && previousState != State.LOADING && previousState != State.PARAMETERIZING && previousState != State.RUNNING
					&& previousState != State.PERSISTING && previousState != State.INTERPOLATING) {
				fail("Jahr " + year + " INTERPOLATING folgt auf späteren Status: " + previousState);
			} else {
				break;
			}
		case POSTPROCESSING:
			if (previousState != State.WAITING && previousState != State.LOADING && previousState != State.PARAMETERIZING && previousState != State.RUNNING
					&& previousState != State.PERSISTING && previousState != State.INTERPOLATING && previousState != State.POSTPROCESSING) {
				fail("Jahr " + year + " POSTPROCESSING folgt auf späteren Status: " + previousState);
			} else {
				break;
			}
		case FINISHED:
			if (previousState == State.FINISHEDERROR) {
				fail("Jahr " + year + " FINISHED folgt auf FINISHEDERROR: " + previousState);
			} else {
				break;
			}
		case FINISHEDERROR:
			if (previousState == State.FINISHED) {
				fail("Jahr " + year + "FINISHEDERROR folgt auf FINISHED!");
			} else {
				break;
			}
		case ERROR:
			break;
		default:
			fail("Jahr " + year + "Unbekannter Status!");
		}
	}

	/**
	 * @param description Die mit den Konstanten zu vergleichende Beschreibung.
	 */
	private void assertEqualDescription(final UserDefinedDescription description) {
		Assert.assertEquals(DatabaseTestUtils.TESTBUSINESS, description.getBusinessModelDescription());
		Assert.assertEquals(DatabaseTestUtils.TESTCREATOR, description.getCreator());
		Assert.assertEquals(DatabaseTestUtils.TESTINVESTMENT, description.getInvestmentCustomerSide());
		Assert.assertEquals(DatabaseTestUtils.TESTAUGENMERK, description.getParameterAttention());
	}

	/**
	 * Testet ob der Simulationslauf am Ende die korrekte Anzahl beendeter Simulationsschritte angibt.
	 */
	@Test
	public void testSimulationSteps() throws JsonParseException, JsonMappingException, IOException {
		final String content = DatabaseTestUtils.getParameterText(TestFiles.DAYS_3.make());
		ServerTestUtils.getInstance();
		final long id = ServerTestUtils.startSimulation(content);

		final String resultstring = ServerTestUtils.waitForSimulationEnd(id);
		LOG.info("GET: " + resultstring + " " + resultstring.getClass());

		final ObjectMapper om = new ObjectMapper();
		final IntermediarySimulationStatus iss = om.readValue(resultstring, IntermediarySimulationStatus.class);

		MatcherAssert.assertThat(iss.getFinishedsteps(), Matchers.equalTo(iss.getSimulationsteps()));
	}
}
