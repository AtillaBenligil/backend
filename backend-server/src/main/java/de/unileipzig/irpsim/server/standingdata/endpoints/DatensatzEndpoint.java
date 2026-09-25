package de.unileipzig.irpsim.server.standingdata.endpoints;

import java.util.*;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManager;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManagerProxy;
import de.unileipzig.irpsim.core.standingdata.data.Datensatz;
import de.unileipzig.irpsim.core.standingdata.data.Stammdatum;
import de.unileipzig.irpsim.core.security.ResourceType;
import de.unileipzig.irpsim.server.data.Responses;
import de.unileipzig.irpsim.server.security.ResourceAccess;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Path("/datensatz")
@Api(value = "/datensatz", tags = "Datensatz", description = "Liefert das Stammdatum zu einem Datensatz.")
public class DatensatzEndpoint {

   @GET
   @Produces(MediaType.APPLICATION_JSON)
   @ApiOperation(value = "Gibt das Stammdatum zu beliebig vielen Datensätzen zurück. Falls kein Datensatz übergeben wird, wird die Zuordnung Stammdatum auf Datensatz für alle Stammdaten und Datensätze ausgegeben.", notes = "")
   @ApiResponses(value = {
         @ApiResponse(code = 400, message = "Invalid IDs supplied"),
         @ApiResponse(code = 404, message = "Not Found") })
   public Response getMultipleStammdatumForDatensatz(@QueryParam("id") final List<Integer> ids, @Context final SecurityContext securityContext) {
      final Response refused = StandingDataAccess.checkDatensaetzeReadable(securityContext, ids);
      if (refused != null) {
         return refused;
      }

      try (final ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
         final Session session = (Session) em.getDelegate();
         if (ids.size() == 0) {
            final Map<Integer, List<Datensatz>> result = getAllDatensatz(session);
            // Ohne Kennungen wird die Zuordnung aller Stammdaten geliefert; nicht
            // sichtbare Stammdaten werden daraus entfernt.
            final java.util.function.Predicate<Long> visible = ResourceAccess.visibilityFilter(securityContext, ResourceType.STAMMDATUM);
            result.keySet().removeIf(stammdatumId -> !visible.test(stammdatumId.longValue()));

            final ObjectMapper mapper = new ObjectMapper();
            final String resultString = mapper.writerWithView(Object.class).writeValueAsString(result);
            return Response.ok(resultString).build();
         } else {

            final CriteriaBuilder cBuilder = em.getCriteriaBuilder();
            final CriteriaQuery<Datensatz> dsQuery = cBuilder.createQuery(Datensatz.class);
            final Root<Datensatz> dsRoot = dsQuery.from(Datensatz.class);
            final JSONArray arr = new JSONArray();

            for (final int datensatzId : ids) {

               final List<Datensatz> datensatz = em.createQuery(dsQuery.where(cBuilder.equal(dsRoot.get("id"), datensatzId))).getResultList();
               if (datensatz.size() == 0) {
                  return Responses.badRequestResponse("Datensatz " + datensatzId + " existiert nicht.");
               }

               Datensatz datensatzDB = datensatz.get(0);
               if (datensatzDB.getStammdatum() == null) {
                  return Responses.badRequestResponse("Datensatz " + datensatzId + " existiert, besitzt aber kein Stammdatum (Default-Datensatz).");
               }
               addStammdatumMapping(arr, datensatzId, datensatzDB);
            }

            return Response.ok(arr.toString()).build();
         }
      } catch (final Throwable e) {
         e.printStackTrace();
         return Responses.errorResponse(e);
      }
   }

   private void addStammdatumMapping(final JSONArray arr, final int datensatzId, Datensatz datensatzDB) {
      final int stammdatumId = datensatzDB.getStammdatum().getId();

      final JSONObject jsonObject = new JSONObject();
      jsonObject.put("stammdatum", stammdatumId);
      jsonObject.put("datensatz", datensatzId);

      arr.put(jsonObject);
   }

   private Map<Integer, List<Datensatz>> getAllDatensatz(final Session session) {
      CriteriaBuilder builder = session.getCriteriaBuilder();
      CriteriaQuery<Datensatz> criteria = builder.createQuery(Datensatz.class);
      Root<Datensatz> queryRoot = criteria.from(Datensatz.class);

      Predicate likeRestrictions = builder.and(
            builder.equal(queryRoot.get("aktiv"), true)//,
            /*builder.or(
                  //doesn't have the field?
                  builder.equal(queryRoot.get("evaluable"), true),
                  builder.isNull(queryRoot.get("evaluable"))
            )*/
      );
      criteria.select(queryRoot).where(likeRestrictions);

      List<Datensatz> datensatzList = session.createQuery(criteria).list();

      final Map<Integer, List<Datensatz>> result = new HashMap<>();
      for (final Datensatz datensatz : datensatzList) {
         if (datensatz.getStammdatum() != null) {
            final int stammdatumId = datensatz.getStammdatum().getId();
            List<Datensatz> data = result.get(stammdatumId);
            if (data == null) {
               data = new LinkedList<>();
               result.put(stammdatumId, data);
            }
            data.add(datensatz);
         }
      }

      builder = session.getCriteriaBuilder();
      CriteriaQuery<Stammdatum> StammDatumCriteria = builder.createQuery(Stammdatum.class);
      Root<Stammdatum> queryRootSD = StammDatumCriteria.from(Stammdatum.class);

      StammDatumCriteria.select(queryRootSD);
      for (final Stammdatum sd : session.createQuery(StammDatumCriteria).list()) {
         if (!result.containsKey(sd.getId())) {
            result.put(sd.getId(), new LinkedList<>());
         }
      }
      return result;
   }
}
