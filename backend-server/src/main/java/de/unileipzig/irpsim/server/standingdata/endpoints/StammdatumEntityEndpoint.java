package de.unileipzig.irpsim.server.standingdata.endpoints;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.hibernate.Session;
import org.hibernate.Transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.unileipzig.irpsim.core.security.ResourceType;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManager;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManagerProxy;
import de.unileipzig.irpsim.core.standingdata.StaticDataUtil;
import de.unileipzig.irpsim.core.standingdata.SzenarioSet;
import de.unileipzig.irpsim.core.standingdata.data.AlgebraicData;
import de.unileipzig.irpsim.core.standingdata.data.Datensatz;
import de.unileipzig.irpsim.core.standingdata.data.Stammdatum;
import de.unileipzig.irpsim.server.data.Responses;
import de.unileipzig.irpsim.server.security.ResourceAccess;
import de.unileipzig.irpsim.server.standingdata.endpoints.utils.AlgebraicDataUpdater;
import de.unileipzig.irpsim.server.standingdata.excel.ImportTemplateGenerator;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Path("/stammdaten")
@Api(value = "/stammdaten/{id}", tags = "Stammdaten")
public class StammdatumEntityEndpoint {
	public static final ObjectMapper MAPPER = new ObjectMapper();
	private static final Logger LOG = LogManager.getLogger(StammdatumEntityEndpoint.class);

	@Path("/{id}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Gibt die Informationen eines Stammdatums zurück", notes = "Gibt die Informationen eines Stammdatums zurück.")
	@ApiResponses(value = {
			@ApiResponse(code = 400, message = "Invalid ID supplied"),
			@ApiResponse(code = 404, message = "Not Found") })
	public final Response getConcreteJSON(@ApiParam(value = "Stammdatum-ID", required = true) @PathParam("id") final int id) throws JsonProcessingException {
		try (final ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
			LOG.debug("Suche nach: {}", id);

			final Stammdatum stammdatum = em.find(Stammdatum.class, id);
			if (stammdatum.isSetElemente1IsNull()) {
				stammdatum.setSetElemente1(null);
			}
			if (stammdatum.isSetElemente2IsNull()) {
				stammdatum.setSetElemente2(null);
			}
			final String result = MAPPER.writeValueAsString(stammdatum);
			return Response.ok(result).build();
		} catch (final Exception e) {
			e.printStackTrace();
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
	}

	@Path("/{id}/excel")
	@GET
	@Produces({ "text/plain" })
	@ApiOperation(value = "Gibt die Informationen eines Stammdatums zurück", notes = "Gibt die Informationen eines Stammdatums zurück.")
	@ApiResponses(value = {
			@ApiResponse(code = 400, message = "Invalid ID supplied"),
			@ApiResponse(code = 404, message = "Not Found") })
	public final Response getExcel(final @PathParam("id") int id) throws JsonProcessingException {
	   try (final ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {

			LOG.debug("Suche nach: {}", id);
			final Stammdatum stammdatum = em.find(Stammdatum.class, id);
			if (stammdatum == null) {
				return Responses.badRequestResponse("Stammdatum " + id + " ist nicht vorhanden.");
			}
			if (stammdatum.isAbstrakt() == true) {
				return Responses.badRequestResponse("Stammdatum " + id + " ist abstrakt.");
			}

			final String wrongInput = StaticDataUtil.fillStammdatum(stammdatum);
			if (!"".equals(wrongInput)) {
				return Responses.badRequestResponse(wrongInput);
			}

			final List<AlgebraicData> list = getAlgebraicDataList(em, stammdatum);

			int horizont;
			if (list.size() > 0) {
				horizont = list.get(0).getJahr() - stammdatum.getBezugsjahr() - 1;
			} else {
				horizont = stammdatum.getPrognoseHorizont();
			}
			LOG.debug("Horizont: " + horizont);
			if (horizont < 0) {
				return Responses.badRequestResponse("Algebraischer Datensatz für Bezugsjahr " + stammdatum.getBezugsjahr() + " definiert.");
			}
			final SzenarioSet set = em.find(SzenarioSet.class, stammdatum.getBezugsjahr());
			if (set == null) {
				return Responses.badRequestResponse("Szenarioset für Bezugsjahr " + stammdatum.getBezugsjahr() + " nicht definiert.");
			}
			final ImportTemplateGenerator importTemplateGenerator = new ImportTemplateGenerator("", stammdatum, set);
			importTemplateGenerator.createSheet(horizont);

			final XSSFWorkbook excel = importTemplateGenerator.getWorkbook();
			final StreamingOutput streamOutput = new StreamingOutput() {
				@Override
				public void write(final OutputStream output) throws IOException, WebApplicationException {
					try {
						excel.write(output);
					} catch (final Exception e) {
						throw new WebApplicationException(e);
					}
				}
			};
			return Response.ok(streamOutput)
					.header("Content-Disposition", "attachment; filename=\"" + stammdatum.getBezugsjahr() + " - " + stammdatum.getTyp() + " - " + stammdatum.getName() + ".xlsx\"").build();
		} catch (final Throwable e) {
			e.printStackTrace();
			return Responses.errorResponse(e);
		}
	}

	@Path("/{id}/excel")
	@PUT
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@ApiOperation(value = "Importiert alle Excel-Daten eines Stammdatums", notes = "Importiert alle Excel-Daten eines Stammdatums")
	@ApiResponses(value = {
			@ApiResponse(code = 400, message = "Invalid ID supplied"),
			@ApiResponse(code = 404, message = "Not Found") })
	public final Response putExcel(final @PathParam("id") int id, @FormDataParam("file") final InputStream fileInputStream,
			@FormDataParam("file") final FormDataContentDisposition contentDispositionHeader) throws EncryptedDocumentException, InvalidFormatException, IOException, InterruptedException {
		try {
			return DataUploader.uploadData(WorkbookFactory.create(fileInputStream), id);
		} catch (final Throwable t) {
			t.printStackTrace();
			return Responses.errorResponse(t);
		}
	}

	@Path("/{id}")
	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Ändert ein Stammdatum", notes = "Ändert ein bestehendes Stammdatum.")
	public final Response putStammdatum(final @PathParam("id") int id, final Stammdatum stammdatum) throws JsonProcessingException {
		stammdatum.setId(id);
		try (final ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
			final Session session = (Session) em.getDelegate();
			final Stammdatum old = em.find(Stammdatum.class, id);
			if (old == null) {
				return Responses.badRequestResponse("Stammdatum " + id + " nicht vorhanden");
			} else {
				if (stammdatum.getBezugsjahr() != null) {
					final SzenarioSet prognoseszenarien = em.find(SzenarioSet.class, stammdatum.getBezugsjahr());
					if (prognoseszenarien == null || prognoseszenarien.getSzenarien().size() == 0) {
						return Responses.badRequestResponse("Prognoseszenarien für " + stammdatum.getBezugsjahr() + " sind nicht definiert; das Stammdatum kann nicht hinzugefügt werden.");
					}
				}
				if (stammdatum.getReferenz() != null) {
					final Stammdatum referenz = em.find(Stammdatum.class, stammdatum.getReferenz().getId());
					old.setReferenz(referenz);
				}

				stammdatum.setIsNulldata();

				final Stammdatum copy = new Stammdatum(stammdatum);
				StaticDataUtil.fillStammdatum(copy);
				final Stammdatum equal = StaticDataUtil.findEqualStammdatum(stammdatum, em);
				if (equal != null) {
					return Responses.badRequestResponse("Stammdatum mit gleichem Namen, Typ und Bezugsjahr vorhanden: " + equal.getId());
				}

				final EntityTransaction et = em.getTransaction();
				et.begin();
				System.out.println("Objekt: " + MAPPER.writeValueAsString(stammdatum));
				old.copyFrom(stammdatum);
				session.update(old);
				et.commit();

				new AlgebraicDataUpdater().adjustAlgebraicData(session, em, stammdatum);

				return Response.ok().build();
			}
		} catch (final Throwable e) {
			e.printStackTrace();
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
	}

	@Path("/{id}")
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Löscht Stammdatum", notes = "Es wird das Stammdatum mit dem übergebenen Typ gelöscht.")
	@ApiResponses(value = {
			@ApiResponse(code = 400, message = "Invalid ID supplied"),
			@ApiResponse(code = 404, message = "Not Found") })
	public final Response deleteStammdatum(final @PathParam("id") int id) throws JsonProcessingException {
		if (DataUploader.isProcessed(id)) {
			return Responses.badRequestResponse("Stammdatum " + id + " wird gerade bearbeitet; bitte warten Sie mit dem Löschen bis zum Ende der Bearbeitung.");
		}

		try (final ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
			final Session s = (Session) em.getDelegate();
			final Stammdatum deleteStammdatum = em.find(Stammdatum.class, id);
			if (deleteStammdatum != null) {
				return deleteStammdatum(id, em, s, deleteStammdatum);
			} else {
				return Responses.badRequestResponse("Stammdatum nicht vorhanden");
			}
		} catch (final Throwable e) {
			e.printStackTrace();
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
	}

   private List<AlgebraicData> getAlgebraicDataList(final ClosableEntityManager em, final Stammdatum stammdatum){
	   final CriteriaBuilder cBuilder = em.getCriteriaBuilder();
	   final CriteriaQuery<AlgebraicData> algebraicDataQuery = cBuilder.createQuery(AlgebraicData.class);
	   final Root<AlgebraicData> algebraicDataRoot = algebraicDataQuery.from(AlgebraicData.class);
	   final Predicate equalStammdatum = cBuilder.equal(algebraicDataRoot.get("stammdatum"), stammdatum);

	   algebraicDataQuery.where(equalStammdatum).orderBy(cBuilder.asc(algebraicDataRoot.get("jahr")));

	   return em.createQuery(algebraicDataQuery).setFirstResult(0).setMaxResults(1).getResultList();
	}

	private Response deleteStammdatum(final int id, final ClosableEntityManager em, final Session s,
			final Stammdatum deleteStammdatum) throws JsonProcessingException {
		if (StammdatumImporter.isActive()) {
			return Responses.badRequestResponse("Es läuft gerade ein Import. Löschen ist aktuell nicht möglich.");
		}

		final List<Stammdatum> referenzStammdaten = getStammdatumList(em, deleteStammdatum);
		
		if (referenzStammdaten.size() > 0) {
			final List<String> idList = referenzStammdaten.stream().map(stammdatum -> "" + stammdatum.getId()).collect(Collectors.toList());
			return Responses.badRequestResponse("Es zeigen noch die Stammdaten " + MAPPER.writeValueAsString(idList) + " auf " + id + ", deshalb ist ein Löschen nicht möglich.");
		}

		final String queryString = "SELECT ad.stammdatum FROM AlgebraicData ad JOIN ad.variablenZuordnung zuordnung WHERE zuordnung.stammdatum = :id AND ad.stammdatum != :id";
		final TypedQuery<Stammdatum> query = em.createQuery(queryString, Stammdatum.class);
		query.setParameter("id", deleteStammdatum);
		final List<Stammdatum> usingStammdaten = query.getResultList();
		if (usingStammdaten.size() > 0) {
			final List<String> idList = usingStammdaten.stream().map(stammdatum -> Integer.toString(stammdatum.getId())).collect(Collectors.toList());
			return Responses.badRequestResponse(
					"Es zeigen noch Variablen der algenbraischen Datensätze der Stammdaten " + MAPPER.writeValueAsString(idList) + " auf Stammdatum " + id
							+ ", deshalb ist ein Löschen nicht möglich.");
		}

		final Transaction t = s.beginTransaction();
		final List<Datensatz> referenzDatensatz = getReferenzDatensatz(em, deleteStammdatum);

		for (final Datensatz datensatz : referenzDatensatz) {
			s.delete(datensatz);
		}
		s.delete(deleteStammdatum);
		t.commit();
		// Die Rechte des gelöschten Stammdatums werden mit entfernt, damit keine
		// verwaisten Einträge zurückbleiben.
		ResourceAccess.getService().revokeAll(ResourceType.STAMMDATUM, id);
		return Responses.okResponse("Löschen erfolgreich", "Stammdatum erfolgreich gelöscht.");
	}

   private List<Datensatz> getReferenzDatensatz(final ClosableEntityManager em, final Stammdatum deleteStammdatum) {
      final CriteriaBuilder cBuilder = em.getCriteriaBuilder();
      final CriteriaQuery<Datensatz> datensatzQuery = cBuilder.createQuery(Datensatz.class);
      final Root<Datensatz> datensatzRoot = datensatzQuery.from(Datensatz.class);
      final Predicate equalReferenz = cBuilder.equal(datensatzRoot.get("stammdatum"), deleteStammdatum);

      return em.createQuery(datensatzQuery.where(equalReferenz)).getResultList();
   }

	private List<Stammdatum> getStammdatumList(final ClosableEntityManager em, final Stammdatum deleteStammdatum) {
      final CriteriaBuilder cBuilder = em.getCriteriaBuilder();
      final CriteriaQuery<Stammdatum> stammdatumQuery = cBuilder.createQuery(Stammdatum.class);
      final Root<Stammdatum> stammdatumRoot = stammdatumQuery.from(Stammdatum.class);
      final Predicate equalReferenz = cBuilder.equal(stammdatumRoot.get("referenz"), deleteStammdatum);

      return em.createQuery(stammdatumQuery.where(equalReferenz)).getResultList();
   }
}
