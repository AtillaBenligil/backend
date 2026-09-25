package de.unileipzig.irpsim.server.standingdata.transfer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityTransaction;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManager;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManagerProxy;
import de.unileipzig.irpsim.core.standingdata.AddData;
import de.unileipzig.irpsim.core.standingdata.StaticDataUtil;
import de.unileipzig.irpsim.core.standingdata.TimeseriesValue;
import de.unileipzig.irpsim.core.standingdata.data.Stammdatum;
import de.unileipzig.irpsim.core.security.Permission;
import de.unileipzig.irpsim.core.security.ResourceType;
import de.unileipzig.irpsim.core.security.SubjectType;
import de.unileipzig.irpsim.server.security.ResourceAccess;

public class TransferDataImporter {

	private static final Logger LOG = LogManager.getLogger(TransferDataImporter.class);

	private final TransferData transferData;
	private final String owner;
	private final Map<Integer, Integer> idChangeMapping = new HashMap<>();
	private final Map<Integer, List<TransferDatensatz>> stammdatumDatensatzMap = new HashMap<>();

	public TransferDataImporter(final TransferData transferData) {
		this(transferData, null);
	}

	/**
	 * @param transferData Die zu importierenden Stammdaten
	 * @param owner Der Benutzer, der das Schreibrecht an den importierten Stammdaten erhält, oder null
	 */
	public TransferDataImporter(final TransferData transferData, final String owner) {
		this.transferData = transferData;
		this.owner = owner;

		for (final Stammdatum stammdatum : transferData.getStammdaten()) {
			stammdatumDatensatzMap.put(stammdatum.getId(), new LinkedList<>());
		}

		for (final TransferDatensatz datensatz : transferData.getDaten()) {
			final List<TransferDatensatz> datensatzList = stammdatumDatensatzMap.get(datensatz.getStammdatumId());
			datensatzList.add(datensatz);
		}
	}

	public List<ExistingStammdatum> importTransferData() {
		final List<ExistingStammdatum> existingData = new LinkedList<>();
		try (final ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
			while (transferData.getStammdaten().size() > 0) {
				final int startsize = transferData.getStammdaten().size();
				for (final Iterator<Stammdatum> iterator = transferData.getStammdaten().iterator(); iterator
						.hasNext();) {
					final Stammdatum stammdatum = iterator.next();
					importStammdatum(existingData, em, iterator, stammdatum);
				}
				if (startsize == transferData.getStammdaten().size()) {
					break;
				}
			}
		}
		return existingData;
	}

	private void importStammdatum(final List<ExistingStammdatum> existingData, final ClosableEntityManager em,
	      final Iterator<Stammdatum> iterator, final Stammdatum stammdatum) {

	   final CriteriaBuilder cBuilder = em.getCriteriaBuilder();
	   final CriteriaQuery<Stammdatum> sdQuery = cBuilder.createQuery(Stammdatum.class);
	   final Root<Stammdatum> sdRoot = sdQuery.from(Stammdatum.class);
	   final Predicate[] restrictions = new Predicate[] {
            cBuilder.equal(sdRoot.get("name"), stammdatum.getName()),
            cBuilder.equal(sdRoot.get("typ"), stammdatum.getTyp()),
            cBuilder.equal(sdRoot.get("bezugsjahr"), stammdatum.getBezugsjahr())
      };
	   final List<Stammdatum> stammdatenEqual = em.createQuery(sdQuery.where(restrictions)).getResultList();

	   if (stammdatenEqual.size() > 0) {
			final ExistingStammdatum ex = new ExistingStammdatum();
			ex.setExistingId(stammdatenEqual.get(0).getId());
			ex.setImportId(stammdatum.getId());
			existingData.add(ex);
			iterator.remove();
		} else {
			if (stammdatum.getReferenz() == null) {
				importStammdatum(em, iterator, stammdatum);
			} else if (idChangeMapping.containsKey(stammdatum.getReferenz().getId())) {
				final Stammdatum referenz = em.find(Stammdatum.class,
						idChangeMapping.get(stammdatum.getReferenz().getId()));
				stammdatum.setReferenz(referenz);
				importStammdatum(em, iterator, stammdatum);
			} else {
				LOG.debug(
						"{} kann noch nicht importiert werden, da Referenz auf {} vorhanden ist und erst {} importiert wurden.",
						stammdatum.getId(), stammdatum.getReferenz().getId(), idChangeMapping.keySet());
			}
		}
	}

	private int importStammdatum(final ClosableEntityManager em, final Iterator<Stammdatum> iterator,
			final Stammdatum stammdatum) {
		final EntityTransaction et = em.getTransaction();
		et.begin();

		final int oldId = stammdatum.getId();
		stammdatum.setId(0);
		em.persist(stammdatum);
		idChangeMapping.put(oldId, stammdatum.getId());
		iterator.remove();

		for (final TransferDatensatz transferDatensatz : stammdatumDatensatzMap.get(oldId)) {
			final AddData addData = new AddData();
			addData.setJahr(transferDatensatz.getJahr());
			addData.setSzenario(transferDatensatz.getSzenarioStelle());
			addData.setValues(transferDatensatz.getData().toArray(new TimeseriesValue[0]));
			StaticDataUtil.importDataNoTransaction(addData, em, stammdatum);
		}

		et.commit();

		// Wie beim Anlegen erhält der Importierende das Schreibrecht; ohne
		// Eintrag wären importierte Stammdaten für alle lesbar.
		if (owner != null) {
			ResourceAccess.getService().grant(ResourceType.STAMMDATUM, stammdatum.getId(), SubjectType.USER, owner, Permission.WRITE);
		}

		return stammdatum.getId();

	}

	public int getImportCount() {
		return idChangeMapping.size();
	}
}