package de.unileipzig.irpsim.server.optimisation.endpoints;

import static de.unileipzig.irpsim.server.data.Responses.errorResponse;

import java.util.*;
import java.util.function.Function;

import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
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

import de.unileipzig.irpsim.core.data.simulationparameters.OptimisationScenario;
import de.unileipzig.irpsim.core.simulation.data.json.UserDefinedDescription;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.transform.Transformers;

import de.unileipzig.irpsim.core.Constants;
import de.unileipzig.irpsim.core.security.Permission;
import de.unileipzig.irpsim.core.security.ResourceType;
import de.unileipzig.irpsim.core.simulation.data.json.IntermediarySimulationStatus;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManager;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManagerProxy;
import de.unileipzig.irpsim.core.simulation.data.persistence.OptimisationJobPersistent;
import de.unileipzig.irpsim.core.simulation.data.persistence.OptimisationYearPersistent;
import de.unileipzig.irpsim.core.simulation.data.persistence.State;
import de.unileipzig.irpsim.server.optimisation.Job;
import de.unileipzig.irpsim.server.optimisation.queue.OptimisationJobHandler;
import de.unileipzig.irpsim.server.security.ResourceAccess;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * Gibt die Stati aller Simulationsjobs zurück.
 *
 * @author reichelt
 */
@Path("/simulations")
@Api(value = "/simulations/states", tags = "Simulation", description = "Gibt Simulationsstati zurück")
public class OptimisationStateEndpoint {//TODO Umbenennen StateEndpoint

   private static final Logger LOG = LogManager.getLogger(OptimisationStateEndpoint.class);

   /**
    * @return Gibt die Stati aller Simulationsjobs zurück.
    */
   @Path("/states")
   @GET
   @Produces(MediaType.APPLICATION_JSON)
   @ApiOperation(value = "Gibt die Stati aller Optimierungsjobs zurück.", notes = "Gibt die Stati aller Optimierungsjobs zurück. Dabei werden auch die benutzerdefinierten Daten ausgegeben.")
   @ApiResponses(value = { @ApiResponse(code = 200, message = "Ok") })
   public final Response getAllSimulationMetadata(@QueryParam("modeldefinition") int modeldefinition,
         @Context final SecurityContext securityContext) {
      LOG.info("Getting states");
      try (final ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
         final Session session = em.unwrap(Session.class);

         final List<OptimisationJobPersistent> persistentJobs = loadJobs(modeldefinition, session, securityContext);
         final Map<Long, OptimisationJobPersistent> allJobs = new HashMap<>();
         for (final OptimisationJobPersistent job : persistentJobs) {
            LOG.info("Job: {} Ende: {} Hinzufügen zum sortieren: {}", job.getId(), job.getEnd(), job.getEnd() != null && job.getState() == State.FINISHED);
            allJobs.put(job.getId(), job);
         }
         getYeardata(em, allJobs);

         final List<IntermediarySimulationStatus> states = generateStatesList(allJobs, securityContext);

         final String result = Constants.MAPPER.writeValueAsString(states);
         return Response.status(Response.Status.OK).entity(result).build();
      } catch (final Exception e) {
         e.printStackTrace();
         return errorResponse(e, "Simulationsstatus");
      }
   }

   private List<IntermediarySimulationStatus> generateStatesList(final Map<Long, OptimisationJobPersistent> allJobs,
         final SecurityContext securityContext) {
      final List<IntermediarySimulationStatus> states = new ArrayList<>();
      addActiveJobs(states, allJobs, securityContext);
      for (final OptimisationJobPersistent job : allJobs.values()) {
         states.add(job.getOptimisationState());
      }
      Collections.sort(states, new Comparator<IntermediarySimulationStatus>() {
         @Override
         public int compare(final IntermediarySimulationStatus o1, final IntermediarySimulationStatus o2) {
            return (int) (o1.getId() - o2.getId());
         }
      });
      return states;
   }

   private void getYeardata(final ClosableEntityManager em, final Map<Long, OptimisationJobPersistent> allJobs) {
      if (allJobs.size() > 0) {
         List<Long> jobid = new LinkedList<>(allJobs.keySet());
         final String queryString = "FROM OptimisationYearPersistent year WHERE year.job.id IN :ids";
         TypedQuery<OptimisationYearPersistent> query = em.createQuery(queryString, OptimisationYearPersistent.class);
         query.setParameter("ids", jobid);

         List<OptimisationYearPersistent> years = query.getResultList();
         for (final OptimisationYearPersistent year : years) {
            final OptimisationJobPersistent job = allJobs.get(year.getJob().getId());
            job.getYears().add(year);
         }
      }
   }

   /**
    * Ergänzt die laufenden Aufträge aus dem Arbeitsspeicher.
    *
    * Diese Aufträge stammen nicht aus der Datenbankabfrage und durchlaufen deren
    * Sichtbarkeitsbedingung daher nicht; sie werden hier einzeln geprüft.
    */
   private void addActiveJobs(final List<IntermediarySimulationStatus> states, final Map<Long, OptimisationJobPersistent> allJobs,
         final SecurityContext securityContext) {
      for (final Job job : OptimisationJobHandler.getInstance().getActiveJobs()) {
         if (!ResourceAccess.isPermitted(securityContext, ResourceType.JOB, job.getId(), Permission.READ)) {
            continue;
         }
         final IntermediarySimulationStatus state = job.getIntermediaryState();
         if (!states.contains(state)) {
            states.add(state);
            allJobs.remove(state.getId());
         }
      }
   }

   private List<OptimisationJobPersistent> loadJobs(int modeldefinition, final Session session, final SecurityContext securityContext) {

      CriteriaBuilder builder = session.getCriteriaBuilder();
      CriteriaQuery<Tuple> query = builder.createQuery(Tuple.class);
      Root<OptimisationJobPersistent> queryRoot = query.from(OptimisationJobPersistent.class);

      Predicate likeRestrictions = builder.and(
            builder.equal(queryRoot.get("modeldefinition"), modeldefinition)
      );

      query.multiselect(
            queryRoot.get("id").alias("id"),
            queryRoot.get("end").alias("end"),
            queryRoot.get("creation").alias("creation"),
            queryRoot.get("start").alias("start"),
            queryRoot.get("error").alias("error"),
            queryRoot.get("finishedsteps").alias("finishedsteps"),
            queryRoot.get("modelVersionHash").alias("modelVersionHash"),
            queryRoot.get("state").alias("state"),
            queryRoot.get("description").alias("description"),
            queryRoot.get("simulationsteps").alias("simulationsteps"));


      // Nicht sichtbare Aufträge werden bereits von der Datenbank ausgeschlossen.
      final Predicate visibility = ResourceAccess.visibilityPredicate(builder, queryRoot.get("id"), ResourceType.JOB, securityContext,
            Function.identity());
      if (modeldefinition != 0) {
         query.where(builder.and(likeRestrictions, visibility));
      } else {
         query.where(visibility);
      }

      final List<Tuple> metaDataList = session.createQuery(query).getResultList();
      final List<OptimisationJobPersistent> jobsList = new LinkedList<>();
      for (Tuple tuple : metaDataList){
         OptimisationJobPersistent opti = new OptimisationJobPersistent();
         opti.setId( tuple.get("id", Long.class));
         opti.setEnd( tuple.get("end", Date.class));
         opti.setCreation( tuple.get("creation", Date.class));
         opti.setStart(tuple.get("start", Date.class));
         opti.setError(tuple.get("error", Boolean.class));
         opti.setFinishedsteps(tuple.get("finishedsteps", Integer.class));
         opti.setModelVersionHash(tuple.get("modelVersionHash", String.class));
         opti.setState( tuple.get("state", State.class));
         opti.setDescription(tuple.get("description", UserDefinedDescription.class));
         opti.setSimulationsteps( tuple.get("simulationsteps", Integer.class));
         jobsList.add(opti);
      }

      return jobsList;
   }
}
