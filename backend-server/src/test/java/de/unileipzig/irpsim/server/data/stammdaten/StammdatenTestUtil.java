package de.unileipzig.irpsim.server.data.stammdaten;

import java.util.LinkedList;

import javax.persistence.EntityTransaction;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.unileipzig.irpsim.core.data.simulationparameters.OptimisationScenario;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManager;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManagerProxy;
import de.unileipzig.irpsim.core.simulation.data.persistence.OptimisationJobPersistent;
import de.unileipzig.irpsim.core.simulation.data.persistence.OptimisationYearPersistent;
import de.unileipzig.irpsim.core.standingdata.DataLoader;
import de.unileipzig.irpsim.core.standingdata.SzenarioSet;
import de.unileipzig.irpsim.core.standingdata.SzenarioSetElement;
import de.unileipzig.irpsim.core.standingdata.data.Datensatz;
import de.unileipzig.irpsim.core.standingdata.data.Person;
import de.unileipzig.irpsim.core.standingdata.data.Stammdatum;
import de.unileipzig.irpsim.core.standingdata.data.Variable;
import de.unileipzig.irpsim.server.utils.RESTCaller;
import de.unileipzig.irpsim.server.utils.ServerTestUtils;

/**
 * Bietet Werkzeuge zur Ausführung der Stammdaten-Tests an.
 */
public class StammdatenTestUtil {
	private static final Logger LOG = LogManager.getLogger(StammdatenTestUtil.class);

	private static final ObjectMapper mapper = new ObjectMapper();

	/**
	 * Räumt die Datenbank auf, indem die relevanten Tabellen Stammdatum und Person geleert werden.
	 */
	public static void cleanUp() {
		LOG.debug("Aufräumen");

		DataLoader.initializeTimeseriesTables();

		try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
			final EntityTransaction transaction = em.getTransaction();
			transaction.begin();
			// verhindert truncate foreign key Schutz, Nicht im Produktionscode verwenden
			em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0;").executeUpdate();
			em.createNativeQuery("TRUNCATE TABLE " + OptimisationJobPersistent.class.getSimpleName()).executeUpdate();
			em.createNativeQuery("TRUNCATE TABLE " + OptimisationYearPersistent.class.getSimpleName()).executeUpdate();
			em.createNativeQuery("TRUNCATE TABLE " + Stammdatum.class.getSimpleName()).executeUpdate();
			em.createNativeQuery("TRUNCATE TABLE " + Datensatz.class.getSimpleName()).executeUpdate();
			em.createNativeQuery("TRUNCATE TABLE " + Person.class.getSimpleName()).executeUpdate();
			em.createNativeQuery("TRUNCATE TABLE " + OptimisationScenario.class.getSimpleName()).executeUpdate();
			em.createNativeQuery("TRUNCATE TABLE " + Variable.class.getSimpleName()).executeUpdate();
			em.createNativeQuery("TRUNCATE TABLE " + SzenarioSet.class.getSimpleName()).executeUpdate();
			em.createNativeQuery("TRUNCATE TABLE " + SzenarioSetElement.class.getSimpleName()).executeUpdate();
			em.createNativeQuery("TRUNCATE TABLE Datensatz_Variable").executeUpdate();
			// Die Kennungen beginnen nach dem Leeren wieder bei 1; ohne diese Zeile
			// erbten neue Ressourcen die Rechte gelöschter Ressourcen gleicher Kennung.
			em.createNativeQuery("TRUNCATE TABLE accesscontrolentry").executeUpdate();

			em.createNativeQuery("DELETE FROM series_data_in WHERE seriesid != 0").executeUpdate();
			em.createNativeQuery("DELETE FROM series_data_out WHERE seriesid != 0").executeUpdate();
			em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1;").executeUpdate();
			transaction.commit();
		}
	}

	public static void createPrognoseszenarien() {
		try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
			final EntityTransaction transaction = em.getTransaction();
			transaction.begin();

			final SzenarioSet test = new SzenarioSet();
			test.setJahr(2016);
			final LinkedList<SzenarioSetElement> szenarien = new LinkedList<>();
			final SzenarioSetElement szenario_asd = new SzenarioSetElement(test, 1, "ASD");
			final SzenarioSetElement szenario_bsd = new SzenarioSetElement(test, 2, "BSD");
			szenarien.add(szenario_asd);
			szenarien.add(szenario_bsd);
			test.setSzenarien(szenarien);

			em.persist(test);
			em.persist(szenario_bsd);
			em.persist(szenario_asd);

			transaction.commit();
		}
	}

	public static int getId(final Response response) {
		final String responseString = response.readEntity(String.class);
		LOG.debug(responseString);
		return new JSONArray(responseString).getInt(0);
	}

	public static int addStammdatum(final Stammdatum stammdatum) throws JsonProcessingException {
		final String jsonString = mapper.writeValueAsString(stammdatum);
		final Response putResponse = RESTCaller.callPutResponse(ServerTestUtils.URI + "stammdaten/", jsonString);
		final int id = StammdatenTestUtil.getId(putResponse);
		stammdatum.setId(id);
		return id;
	}
}
