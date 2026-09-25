package de.unileipzig.irpsim.server.standingdata.endpoints;

import de.unileipzig.irpsim.core.security.Permission;
import de.unileipzig.irpsim.core.security.ResourceType;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManager;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManagerProxy;
import de.unileipzig.irpsim.core.standingdata.StaticDataUtil;
import de.unileipzig.irpsim.core.standingdata.data.Stammdatum;
import de.unileipzig.irpsim.server.data.Responses;
import de.unileipzig.irpsim.server.security.ResourceAccess;
import de.unileipzig.irpsim.server.standingdata.excel.TemplateImporter;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.hibernate.Session;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Path("/stammdaten")
@Api(value = "/stammdaten/excel", tags = "Stammdaten", description = "Ermöglicht es, Excel-Daten hinzuzufügen.")
public class ExcelGroupEndpoint {

	@Path("/excel")
	@PUT
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@ApiOperation(value = "Importiert alle Excel-Daten für ein beliebiges Stammdatum", notes = "Importiert alle Excel-Daten für ein beliebiges Stammdatum")
	@ApiResponses(value = {
			@ApiResponse(code = 400, message = "Invalid ID supplied"),
			@ApiResponse(code = 404, message = "Not Found") })
	public final Response putExcel(@FormDataParam("file") final InputStream fileInputStream,
			@FormDataParam("file") final FormDataContentDisposition contentDispositionHeader, @Context final SecurityContext securityContext)
			throws EncryptedDocumentException, InvalidFormatException, IOException {
		final String filename = contentDispositionHeader.getFileName();
		final Workbook workbook = WorkbookFactory.create(fileInputStream);
		final Stammdatum realStammdatum;
		final Stammdatum excelStammdatum = TemplateImporter.fetchStammdatum(workbook);
		try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
			final Session session = (Session) em.getDelegate();
			realStammdatum = getStammdatum(session, excelStammdatum);
			if (realStammdatum == null) {
				return Responses.badRequestResponse("Datei " + filename + " - Stammdatum nicht vorhanden");
			}
			// Welches Stammdatum befüllt wird, steht erst in der Datei; daher wird
			// das Schreibrecht hier und nicht im pfadbasierten Filter geprüft.
			if (!ResourceAccess.isPermitted(securityContext, ResourceType.STAMMDATUM, realStammdatum.getId(), Permission.WRITE)) {
				return ResourceAccess.forbidden();
			}

			return DataUploader.uploadData(workbook, realStammdatum.getId());
		} catch (final Throwable t) {
			t.printStackTrace();
			return Responses.errorResponse(t);
		}
	}

	/**
	 * Retrieves Stammdatum from Database.
	 *
	 * @param session of the Database connection
	 * @param excelStammdatum Stammdatum for the Query
	 * @return Stammdatum if found in the Database else null
	 */
	private static Stammdatum getStammdatum(final Session session, final Stammdatum excelStammdatum) {
		CriteriaBuilder builder = session.getCriteriaBuilder();
		CriteriaQuery<Stammdatum> criteria = builder.createQuery(Stammdatum.class);
		Root<Stammdatum> queryRoot = criteria.from(Stammdatum.class);

		Predicate likeRestrictions = builder.and(
				builder.or(builder.equal(queryRoot.get("name"), excelStammdatum.getName()),builder.isNull(queryRoot.get("name"))),
				builder.or(builder.equal(queryRoot.get("typ"), excelStammdatum.getTyp()),builder.isNull(queryRoot.get("typ"))),
				builder.or(builder.equal(queryRoot.get("bezugsjahr"), excelStammdatum.getBezugsjahr()),builder.isNull(queryRoot.get("bezugsjahr")))
		);

		criteria.select(queryRoot).where(likeRestrictions);

		final List<Stammdatum> stammdaten = session.createQuery(criteria).list();

		if (stammdaten.size() > 0) {
			for (final Stammdatum candidate : stammdaten) {
				StaticDataUtil.fillStammdatum(candidate);
				if (candidate.getBezugsjahr().intValue() == excelStammdatum.getBezugsjahr().intValue()
						&& candidate.getTyp().equals(excelStammdatum.getTyp())
						&& candidate.getName().equals(excelStammdatum.getName())) {
					return candidate;

				}
			}
		}
		return null;
	}

}
