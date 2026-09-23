package de.unileipzig.irpsim.server.data.simulationparameters;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unileipzig.irpsim.core.data.simulationparameters.OptimisationScenario;
import de.unileipzig.irpsim.core.simulation.data.BackendParameters;
import de.unileipzig.irpsim.core.simulation.data.json.JSONParameters;
import de.unileipzig.irpsim.core.simulation.data.json.JSONParametersMultimodel;
import de.unileipzig.irpsim.core.simulation.data.json.JSONParametersSingleModel;
import de.unileipzig.irpsim.core.simulation.data.json.YearData;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManager;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManagerProxy;
import de.unileipzig.irpsim.core.standingdata.SzenarioSet;
import de.unileipzig.irpsim.server.data.Responses;
import de.unileipzig.irpsim.core.security.Permission;
import de.unileipzig.irpsim.core.security.ResourceType;
import de.unileipzig.irpsim.server.data.modeldefinitions.ModelDefinitionsEndpoint;
import de.unileipzig.irpsim.server.security.ResourceAccess;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;

import javax.persistence.EntityTransaction;
import javax.persistence.Tuple;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static de.unileipzig.irpsim.server.data.Responses.badRequestResponse;
import static de.unileipzig.irpsim.server.data.Responses.errorResponse;

/**
 * Ermöglicht die Verwaltung von Szenarien für die Simulationsmodelle.
 *
 * @author reichelt
 */
@Path("/szenarien")
@Api(value = "/szenarien", tags = "Szenario")
public class ScenarioEndpoint {

   private static final Logger LOG = LogManager.getLogger(ScenarioEndpoint.class);
   private static final ObjectMapper MAPPER = new ObjectMapper();

   /**
    * Liest alle Simulationsparametersätze aus und gibt sie inklusive ihrer Metadaten zurück.
    *
    * @return Die Simulationsparametersätze und ihre Metadaten
    */
   @GET
   @Produces(MediaType.APPLICATION_JSON)
   @ApiOperation(value = "Liefert Szenarien zurück", notes = "Liest alle Szenarien aus und gibt sie inklusive ihrer Metadaten zurück.")
   @ApiResponses(value = { @ApiResponse(code = 200, message = "Ok") })
   public final Response getSimulationParameters(@QueryParam("modeldefinition") int modeldefinition,
         @Context final SecurityContext securityContext) {
      try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
         final Session s = em.unwrap(Session.class);
         final Map<Integer, OptimisationScenario> dataMap = getScenarioMap(modeldefinition, s, securityContext);

         String metadataString = "";
         try {
            metadataString = MAPPER.writeValueAsString(dataMap);
            // final JSONObject jso = new JSONObject(metadataString);
            // for (final Object key : jso.keySet()) {
            // final JSONObject value = (JSONObject) jso.get((String) key);
            // value.remove("data");
            // }
            // metadataString = jso.toString();

         } catch (final JsonProcessingException e1) {
            e1.printStackTrace();
         }
         if (metadataString.length() > 100) {
            LOG.debug("Gebe Szenarien zurück: {}", metadataString.substring(0, 100));
         }
         return Response.ok(metadataString).build();
      } catch (final Exception e) {
         LOG.error(e);
         e.printStackTrace();
         return errorResponse(e, "parametersets");
      }
   }

   private Map<Integer, OptimisationScenario> getScenarioMap(int modeldefinition, final Session s,
         final SecurityContext securityContext) {
      CriteriaBuilder builder = s.getCriteriaBuilder();
      CriteriaQuery<Tuple> query = builder.createQuery(Tuple.class);
      Root<OptimisationScenario> queryRoot = query.from(OptimisationScenario.class);

      Predicate likeRestrictions = builder.and(
            builder.equal(queryRoot.get("modeldefinition"), modeldefinition)
      );

      query.multiselect(
            queryRoot.get("id").alias("id"),
            queryRoot.get("name").alias("name"),
            queryRoot.get("creator").alias("creator"),
            queryRoot.get("description").alias("description"),
            queryRoot.get("modeldefinition").alias("modeldefinition"),
            queryRoot.get("deletable").alias("deletable"),
            queryRoot.get("date").alias("date"),
            queryRoot.get("version").alias("version"));

      // Die Sichtbarkeit wird als zusaetzliche Bedingung in die Abfrage aufgenommen,
      // damit die Einschraenkung bereits in der Datenbank erfolgt und nicht erst
      // nach dem Laden aller Szenarien.
      final Predicate visibility = buildVisibilityPredicate(builder, queryRoot, securityContext);
      if (modeldefinition != 0) {
         query.where(builder.and(likeRestrictions, visibility));
      } else {
         query.where(visibility);
      }

      final List<Tuple> metaDataList = s.createQuery(query).getResultList();
      final Map<Integer, OptimisationScenario> dataMap = new LinkedHashMap<>();
      for (Tuple tuple : metaDataList){
         OptimisationScenario opti = new OptimisationScenario();
         opti.setId( tuple.get("id", Integer.class));
         opti.setName( tuple.get("name", String.class));
         opti.setCreator( tuple.get("creator", String.class));
         opti.setDescription( tuple.get("description", String.class));
         opti.setModeldefinition( tuple.get("modeldefinition", Integer.class));
         opti.setDeletable(tuple.get("deletable", Boolean.class));
         opti.setDate(tuple.get("date", Date.class));
         opti.setVersion(tuple.get("version", String.class));
         dataMap.put(opti.getId(), opti);
      }

      return dataMap;
   }

   /**
    * Bildet die Bedingung, die die Auflistung auf die fuer den Benutzer
    * sichtbaren Szenarien einschraenkt.
    *
    * Szenarien, fuer die keine Rechte vergeben wurden, stammen aus der Zeit vor
    * der Rechteverwaltung und bleiben sichtbar. Alle uebrigen Szenarien werden
    * nur dann aufgefuehrt, wenn dem Benutzer oder einer seiner Gruppen ein
    * Recht an ihnen eingeraeumt wurde.
    *
    * @param builder Der Ersteller der Abfragebedingungen
    * @param root Die Wurzel der Szenarioabfrage
    * @param securityContext Der Sicherheitskontext der Anfrage
    * @return Die Bedingung fuer die sichtbaren Szenarien
    */
   private static Predicate buildVisibilityPredicate(final CriteriaBuilder builder, final Root<OptimisationScenario> root,
         final SecurityContext securityContext) {
      return ResourceAccess.visibilityPredicate(builder, root.get("id"), ResourceType.SCENARIO, securityContext, Long::intValue);
   }

   /**
    * Gibt den Simulationsparametersatz mit der übergebenen Id zurück.
    *
    * @param id Die ID des Simulationsparametersatz
    * @return Der Simulationsparametersatz mit seiner zugehörigen ID
    * @throws JsonParseException Tritt auf wenn Fehler beim parsen der JSON-Daten auftreten
    * @throws JsonMappingException Tritt auf falls Fehler beim mapping auftreten
    * @throws IOException Tritt auf falls Fehler beim lesen und schreiben auftreten
    */
   @GET
   @Produces(MediaType.APPLICATION_JSON)
   @Path("/{id}")
   @ApiOperation(value = "Gibt den Simulationsparametersatz mit der übergebenen Id zurück.", notes = "")
   @ApiResponses(value = { @ApiResponse(code = 200, message = "Ok"), @ApiResponse(code = 204, message = "No Content") })
   public final Response getConcreteSimulationParameters(@PathParam("id") final int id,
         @Context final SecurityContext securityContext) throws JsonParseException, JsonMappingException, IOException {
      LOG.info("Lade Parameter für: {}", id);
      if (!ResourceAccess.isPermitted(securityContext, ResourceType.SCENARIO, id, Permission.READ)) {
         LOG.info("Lesezugriff auf Szenario {} durch {} abgelehnt", id, ResourceAccess.username(securityContext));
         return ResourceAccess.forbidden();
      }
      try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
         LOG.trace("Laden beginnt..");
         final OptimisationScenario spm = em.find(OptimisationScenario.class, id);
         if (spm == null) {
            return badRequestResponse("Parametersatz " + id + " nicht vorhanden.");
         }
         final String data = spm.getData();
         if (data != null) {
            LOG.debug("Parameter: {} {} Daten: {}", spm.getId(), spm.getName(), data.substring(0, 100));
            return Response.ok(data).build();
         } else {
            return Responses.errorResponse("Szenario noch nicht vollständig geladen.");
         }
      } catch (final Exception e) {
         e.printStackTrace();
         return errorResponse(e);
      }
   }

   /**
    * Prüft, ob der Parametersatz mit der übergebenen Id löschbar ist, und löscht ihn.
    *
    * @param id Die ID des Parametersatzes
    * @return Response.ok() bei Erfolg, Response.error() bei Misserfolg
    * @throws JsonParseException Tritt auf wenn Fehler beim parsen der JSON-Daten auftreten
    * @throws JsonMappingException Tritt auf falls Fehler beim mapping auftreten
    * @throws IOException Tritt auf falls Fehler beim lesen und schreiben auftreten
    */
   @DELETE
   @Produces(MediaType.APPLICATION_JSON)
   @Path("/{id}")
   @ApiOperation(value = "Löscht Parameter nach einer bestimmen Id", notes = "Prüft, ob der Parametersatz mit der übergebenen Id löschbar ist, und löscht ihn, falls er löschbar ist.")
   @ApiResponses(value = { @ApiResponse(code = 200, message = "Ok"), @ApiResponse(code = 400, message = "Bad Request") })
   public final Response deleteParameter(@PathParam("id") final int id, @Context final SecurityContext securityContext)
         throws JsonParseException, JsonMappingException, IOException {
      LOG.info("Lade Parameter für: {}", id);
      if (!ResourceAccess.isPermitted(securityContext, ResourceType.SCENARIO, id, Permission.WRITE)) {
         LOG.info("Löschen von Szenario {} durch {} abgelehnt", id, ResourceAccess.username(securityContext));
         return ResourceAccess.forbidden();
      }
      try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
         final Session s = (Session) em.getDelegate();
         final OptimisationScenario spm = s.get(OptimisationScenario.class, id);

         if (spm != null) {
            LOG.debug("Löschbar: {}", spm.isDeletable());
            if (spm.isDeletable()) {
               em.getTransaction().begin();
               s.delete(spm);
               em.getTransaction().commit();
               // Die Rechte des geloeschten Szenarios werden mit entfernt,
               // damit keine verwaisten Eintraege zurueckbleiben.
               ResourceAccess.getService().revokeAll(ResourceType.SCENARIO, id);
               return Response.ok().build();
            } else {
               LOG.debug("Unlöschbar");
               return badRequestResponse("Konfiguration ist unlöschbar", "Löschen");
            }
         } else {
            LOG.debug("Konfiguration ist nicht vorhanden");
            return badRequestResponse("Konfiguration ist nicht vorhanden", "Löschen");
         }
      } catch (final Throwable e) {
         e.printStackTrace();
         return errorResponse(e, "Löschen");
      }
   }

   /**
    * Fügt den übergebenen Simulationsparametersatz in die Datenbank ein.
    *
    * @param simulationParameters Der in die Datenbank einzufügende Datensatz
    * @return Response.ok() bei Erfolg, Response.error() bei Misserfolg
    */
   @PUT
   @Consumes(MediaType.APPLICATION_JSON)
   @ApiOperation(value = "Fügt neuen Simulationsparameter hinzu", notes = "Fügt den übergebenen Parametersatz in die Datenbank ein. "
         + "Dabei werden Metadaten, wie Erstellungsdatum und Modelltyp, automatisch generiert.")
   @ApiResponses(value = { @ApiResponse(code = 200, message = "Ok"), @ApiResponse(code = 400, message = "Bad Request") })
   public final Response createNewSimulationParameters(final String simulationParameters,
         @Context final SecurityContext securityContext) {
      try {
         final String dataString = getDataString(simulationParameters);
         final OptimisationScenario simulationMetadata = MAPPER.readValue(simulationParameters, OptimisationScenario.class);
         simulationMetadata.setDate(new Date());

         Response response;
         simulationMetadata.setData(MAPPER.writeValueAsString(new JSONParametersMultimodel()));
         try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
            response = importMultiModelScenario(dataString, em, simulationMetadata);
         }
         if (response != null) {
            // Ohne diesen Eintrag waere das neue Szenario fuer alle lesbar,
            // aber fuer niemanden bearbeitbar.
            ResourceAccess.grantOwnership(securityContext, ResourceType.SCENARIO, simulationMetadata.getId());
            return response;
         } else {
            return badRequestResponse("Unbekannter ParameterJson Fehler ist aufgetreten.");
         }
      } catch (final Throwable e) {
         e.printStackTrace();
         return errorResponse(e, "Fehler beim Import");
      }
   }

   /**
    * Fügt den übergebenen Simulationsparametersatz in die Datenbank ein.
    *
    * @param simulationParameters Der in die Datenbank einzufügende Datensatz
    * @return Response.ok() bei Erfolg, Response.error() bei Misserfolg
    */
   @PUT
   @Path("/{id}")
   @Consumes(MediaType.APPLICATION_JSON)
   @ApiOperation(value = "Fügt neuen Simulationsparameter hinzu", notes = "Fügt den übergebenen Parametersatz in die Datenbank ein. "
         + "Dabei werden Metadaten, wie Erstellungsdatum und Modelltyp, automatisch generiert.")
   @ApiResponses(value = { @ApiResponse(code = 200, message = "Ok"), @ApiResponse(code = 400, message = "Bad Request") })
   public final Response createNewSimulationParameters(@PathParam("id") final int id, final String simulationParameters,
         @Context final SecurityContext securityContext) {
      if (!ResourceAccess.isPermitted(securityContext, ResourceType.SCENARIO, id, Permission.WRITE)) {
         LOG.info("Änderung an Szenario {} durch {} abgelehnt", id, ResourceAccess.username(securityContext));
         return ResourceAccess.forbidden();
      }
      try {
         try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
            final OptimisationScenario simulationMetadata = em.find(OptimisationScenario.class, id);
            if (!simulationMetadata.isDeletable()) {
               return Responses.badRequestResponse("Szenario ist nicht bearbeitbar.");
            }
            final String dataString = getDataString(simulationParameters);

            return importMultiModelScenario(dataString, em, simulationMetadata);
         }
      } catch (final Throwable e) {
         e.printStackTrace();
         return errorResponse(e, "Fehler beim Import");
      }
   }

   private Response importMultiModelScenario(final String dataString, final ClosableEntityManager em, final OptimisationScenario simulationMetadata)
         throws InterruptedException {

      JSONParametersMultimodel multiModelParameterObject;
      try {
         multiModelParameterObject = MAPPER.readValue(dataString, JSONParametersMultimodel.class);
      } catch (final IOException e) {
         return badRequestResponse(e, "Parameter anlegen");
      }

      List<JSONParameters> models = multiModelParameterObject.getModels();
      JSONParameters model = models.get(0);

//      multiModelParameterObject.getModelDefinition()
      
      int modeldefinition = model.getYears().get(0).getConfig().getModeldefinition();
      simulationMetadata.setVersion(ModelDefinitionsEndpoint.getModelVersion(modeldefinition));
      simulationMetadata.setModeldefinition(multiModelParameterObject.getModeldefinition());

      for (JSONParameters connectedModel : models) {
         String scenarioError = checkForErrors(connectedModel.getYears(), em);
         if (scenarioError.length() > 0) {
            return Responses.badRequestResponse(scenarioError);
         }
      }

      final EntityTransaction et = persistRawScenario(em, simulationMetadata);
      replaceMultiTimeSeriesByReferences(multiModelParameterObject, em, simulationMetadata, et);
      return Response.ok("{\"id\": " + simulationMetadata.getId() + "}").build();
   }

   private EntityTransaction persistRawScenario(final ClosableEntityManager em, final OptimisationScenario simulationMetadata) {
      LOG.debug("Beginne Persistieren");
      final EntityTransaction et = em.getTransaction();
      et.begin();
      em.persist(simulationMetadata);
      em.flush();
      return et;
   }

   private void replaceMultiTimeSeriesByReferences(final JSONParametersMultimodel parameterObject, final ClosableEntityManager em, final OptimisationScenario simulationMetadata,
         final EntityTransaction et) throws InterruptedException {
      final MultiScenarioImportHandler scenarioImportHandler = new MultiScenarioImportHandler(parameterObject, 0, true);
      scenarioImportHandler.handleTimeseries();

      try {
         String scenarioWithReferences = MAPPER.writeValueAsString(scenarioImportHandler.getParameters());
         final String outputString = MAPPER.writeValueAsString(scenarioImportHandler.getParameters().getModels().get(0).getYears().get(0).getTimeseries());
         LOG.debug("Referenzierte Daten: {}", outputString.substring(0, Math.min(outputString.length(), 1000)));
         simulationMetadata.setData(scenarioWithReferences);
         em.merge(simulationMetadata);
         et.commit();
      } catch (final JsonProcessingException e1) {
         e1.printStackTrace();
      }
   }

   private void replaceTimeseriesByReferences(final JSONParametersSingleModel gamsParametersObject, final ClosableEntityManager em, final OptimisationScenario simulationMetadata,
         final EntityTransaction et) throws InterruptedException {
      final ScenarioImportHandler scenarioImportHandler = new ScenarioImportHandler(new BackendParameters(gamsParametersObject), 0, true);
      scenarioImportHandler.handleTimeseries();

      try {
         String scenarioWithReferences = MAPPER.writeValueAsString(scenarioImportHandler.getParameters());
         final String outputString = MAPPER.writeValueAsString(scenarioImportHandler.getParameters().getYears().get(0).getTimeseries());
         LOG.debug("Referenzierte Daten: {}", outputString.substring(0, Math.min(outputString.length(), 1000)));
         simulationMetadata.setData(scenarioWithReferences);
         em.merge(simulationMetadata);
         et.commit();
      } catch (final JsonProcessingException e1) {
         e1.printStackTrace();
      }
   }

   private String checkForErrors(final List<YearData> yearDataList, final ClosableEntityManager em) {
      final SzenarioSet set = em.find(SzenarioSet.class, yearDataList.get(0).getConfig().getYear());
      String szenarioError = "";
      for (final YearData year : yearDataList) {
         if (year != null) {
            LOG.debug(year.getConfig());
            final Integer prognoseSzenario = year.getConfig().getPrognoseszenario();
            if (prognoseSzenario != null && !set.getSzenarien().stream().filter(value -> value.getStelle() == prognoseSzenario).findAny().isPresent()) {
               szenarioError += "In Jahr " + year.getConfig().getYear() + " ist die Szenariostelle " + prognoseSzenario + " nicht definiert, wurde aber im Szenario angegeben. ";
            }
         }
      }
      return szenarioError;
   }

   private static String getDataString(final String parameterstring) {
      final int startIndex = parameterstring.indexOf("\"data\":");
      final int stopIndex = parameterstring.lastIndexOf("}");
      final String substring = parameterstring.substring(startIndex + 7, stopIndex);
      return substring;
   }

}
