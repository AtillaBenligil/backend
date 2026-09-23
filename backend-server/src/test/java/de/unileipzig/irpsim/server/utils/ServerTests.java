/**
 *
 */
package de.unileipzig.irpsim.server.utils;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.client.JerseyClient;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.glassfish.jersey.client.JerseyWebTarget;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;

import de.unileipzig.irpsim.core.testutils.DatabaseTestUtils;
import de.unileipzig.irpsim.core.utils.PersistenceFolderUtil;
import de.unileipzig.irpsim.core.utils.TestFiles;
import de.unileipzig.irpsim.server.connectionLeakUtil.ConnectionLeakUtil;
import de.unileipzig.irpsim.server.marker.RESTTest;

/**
 * Super class for all tests that need to access the server via its HTTP API. _base is there just to avoid some naming conflicts. Especially static methods with @BeforeClass annotations with the same
 * name in parent and child lead to not calling the method in the parent class.
 *
 * @author steffen
 */
@Category({ RESTTest.class })
public abstract class ServerTests {

	private static final Logger LOG = LogManager.getLogger(ServerTests.class);
	protected final JerseyClient jc = new JerseyClientBuilder().build();

	protected final JerseyClient getJerseyClient() {
		return jc;
	}

	private static void innerListFilesAndDirs(final Collection<File> files, final File directory, final IOFileFilter filter, final IOFileFilter subDirectoryFilter) {
		final File[] found = directory.listFiles((java.io.FileFilter) filter);

		if (found != null) {
			for (final File file : found) {
				files.add(file);
				if (!file.isDirectory() || !subDirectoryFilter.accept(file)) {
					continue;
				}
				innerListFilesAndDirs(files, file, filter, subDirectoryFilter);
			}
		}
	}

	/**
	 * @throws IOException
	 * @throws SQLException
	 */
	@BeforeClass
	public static final void startServer() throws IOException, SQLException {
		try {
		   PersistenceFolderUtil.setPersistenceFolder(TestFiles.TEST_PERSISTENCE_FOLDER);
			// final File file = OptimisationJobPersistenceManager.getPersistenceFolder();
			final List<File> files = new LinkedList<>();
			if (PersistenceFolderUtil.getPersistenceFolder().isDirectory()) {
				innerListFilesAndDirs(files, PersistenceFolderUtil.getPersistenceFolder(), new RegexFileFilter("[0-9]*"), FalseFileFilter.INSTANCE);
				innerListFilesAndDirs(files, new File(PersistenceFolderUtil.getPersistenceFolder(), "running_jobs"), new WildcardFileFilter("job_*"), FalseFileFilter.INSTANCE);
				for (final File jobFolder : files) {
					LOG.info("Lösche {}", jobFolder);
					FileUtils.deleteDirectory(jobFolder);
				}
				LOG.debug("Löschen abgeschlossen");
			}

			DatabaseTestUtils.setupDbConnectionHandler();
			ServerTestUtils.getInstance().startServer();

			LOG.debug("ServerTests, @BeforeClass");
			ConnectionLeakUtil.showDbProcessList();
			ConnectionLeakUtil.showConnectionInfos();

		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 *
	 */
	@AfterClass
	public static void shutdownServer() {
		// StammdatenTestUtil.cleanUp();
		ServerTestUtils.getInstance().stopServer();

		LOG.debug("ServerTests, @AfterClass");
		ConnectionLeakUtil.showDbProcessList();
		ConnectionLeakUtil.showConnectionInfos();
	}

	/**
	 * @param parameterset
	 *            Das Parameterset
	 * @return Die id Nummer des Simulationslaufs
	 */
	protected final long startSimulation(final String parameterset) {
		ServerTestUtils.getInstance();
		final long id = ServerTestUtils.startSimulation(parameterset);
		return id;
	}

	/**
	 * @return Die Liste der Parametersets
	 */
	protected final JSONObject getParameterSets() {
      LOG.debug("uri: {}", ServerTestUtils.SZENARIEN_URI);
		final JerseyWebTarget jwt = jc.target(ServerTestUtils.SZENARIEN_URI);
		final Response response = RESTCaller.request(jwt).get();

		final String loadstring = response.readEntity(String.class);
		LOG.trace("Antwort: {}", loadstring);

		final JSONObject jsa = new JSONObject(loadstring);
		return jsa;
	}
	
	protected final JSONObject getParameterSetsFiltered(int modeldefinition) {
      LOG.debug("uri: {}", ServerTestUtils.SZENARIEN_URI + "?modeldefinition=" + modeldefinition);
      final JerseyWebTarget jwt = jc.target(ServerTestUtils.SZENARIEN_URI + "?modeldefinition=" + modeldefinition);
      final Response response = RESTCaller.request(jwt).get();

      final String loadstring = response.readEntity(String.class);
      LOG.trace("Antwort: {}", loadstring);

      final JSONObject jsa = new JSONObject(loadstring);
      return jsa;
   }
}
