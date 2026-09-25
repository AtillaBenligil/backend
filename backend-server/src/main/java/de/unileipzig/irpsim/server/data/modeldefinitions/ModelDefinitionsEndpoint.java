package de.unileipzig.irpsim.server.data.modeldefinitions;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.unileipzig.irpsim.core.Constants;
import de.unileipzig.irpsim.core.simulation.data.json.YearData;
import de.unileipzig.irpsim.models.ModelInformation;
import de.unileipzig.irpsim.models.ModelInformations;
import de.unileipzig.irpsim.server.data.simulationparameters.ScenarioEndpoint;
import de.unileipzig.irpsim.server.optimisation.comparators.modelconnectors.ConnectionType;
import de.unileipzig.irpsim.server.optimisation.comparators.modelconnectors.DependencyType;
import de.unileipzig.irpsim.server.optimisation.comparators.modelconnectors.SyncableParameter;
import de.unileipzig.irpsim.server.optimisation.comparators.modelconnectors.SyncableParameterHandler;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.commons.math3.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Beinhaltet die Modeldefinitionen in der von der UI benötigten Form. Die Definitionen werden automatisch generiert.
 *
 * @author reichelt
 */
@Path("modeldefinitions")
@Api(value = "/modeldefinitions", tags = "Modell-Definitionen", description = "Beinhaltet die Modeldefinitionen in der von der UI benötigten Form. Die Definitionen werden automatisch generiert "
      + "und sind damit konsistent mit den anderen Modelldaten.")
public class ModelDefinitionsEndpoint {
   private static final Logger LOG = LogManager.getLogger(ModelDefinitionsEndpoint.class);

   private static final ObjectMapper mapper = new ObjectMapper();

   /**
    * Liefert alle Modelle, die auf diesem Dateipfad vorliegen.
    *
    * @param clazz Die Klasse deren Ressourcen geladen werden sollen.
    * @param path Pfad zur Modellklasse
    * @return Liste von URIs als String-array
    */
   public static String[] getResourceListing(@SuppressWarnings("rawtypes") final Class clazz, final String path) throws URISyntaxException, IOException {
      URL dirURL = clazz.getClassLoader().getResource(path);
      if (dirURL != null && dirURL.getProtocol().equals("file")) {
         return new File(dirURL.toURI()).list();
      }

      if (dirURL == null) {
         final String me = clazz.getName().replace(".", "/") + ".class";
         dirURL = clazz.getClassLoader().getResource(me);
      }

      if (dirURL.getProtocol().equals("jar")) {
         final Set<String> result = readJarPath(path, dirURL);
         return result.toArray(new String[result.size()]);
      }

      throw new UnsupportedOperationException("Cannot list files for URL " + dirURL);
   }

   private static Set<String> readJarPath(final String path, URL dirURL) throws IOException, UnsupportedEncodingException {
      LOG.debug("Jar-Path");
      final int indexOfJarEnd = dirURL.getPath().indexOf("!"); // strip out only the JAR file
      /* A JAR path */
      final String jarPath = dirURL.getPath().substring(5, indexOfJarEnd);
      final JarFile jar = new JarFile(URLDecoder.decode(jarPath, "UTF-8"));
      final Enumeration<JarEntry> entries = jar.entries(); // gives ALL entries in jar
      final Set<String> result = new HashSet<String>(); // avoid duplicates in case it is a subdirectory
      while (entries.hasMoreElements()) {
         final String name = entries.nextElement().getName();
         if (name.startsWith(path)) { // filter according to the path
            if (!name.replace("/", "").equals(path)) {
               LOG.debug("Kandidat: " + name + " " + path);
               result.add(name.substring(name.indexOf("/") + 1));
            }
         }
      }
      jar.close();
      return result;
   }

   private static ModelInformations information;

   static {
      try {
         information = ModelInformations.deserializeData(getRessourceString("/models.json"));
      } catch (final IOException e) {
         LOG.error(e);
      }
   }

   public static String getModelVersion(YearData yeardata) {
      return getModelVersion(yeardata.getConfig().getModeldefinition());
   }

   public static String getModelVersion(int id) {
      for (ModelInformation info : information.getModelInformations()) {
         if (info.getId() == id) {
            return info.getVersion();
         }
      }
      return "Version for " + id + " not available";
   }
   
   public static ModelInformation getModelInformation(int modeldefinition) {
      for (ModelInformation info : information.getModelInformations()) {
         if (info.getId() == modeldefinition) {
            return info;
         }
      }
      return null;
   }

   public static String getRessourceString(String path) {
      final InputStream input = ScenarioEndpoint.class.getResourceAsStream(path);
      try (Scanner scanner = new Scanner(input, "UTF-8")) {
         final String content = scanner.useDelimiter("\\Z").next();
         return content;
      }
   }

   /**
    * Die Modelldefinitionen sind als Dateien im benötigten JSON-Format abgelegt und werde aus dem Classpath ausgelesen und zurückgegeben.
    *
    * @return Simulationsparameter mit einigen Metadaten
    * @throws JsonProcessingException Tritt auf falls Fehler beim parsen und generieren von JSON-Daten auftreten
    */
   @ApiOperation(value = "Gibt alle Simulationsparameter mit einigen Metadaten zurück", notes = "Die Modelldefinitionen sind als Dateien im benötigten JSON-Format abgelegt und werden "
         + "aus dem Classpath ausgelesen und zurückgegeben.")
   @ApiResponses(value = { @ApiResponse(code = 200, message = "Ok") })
   @GET
   @Produces(MediaType.APPLICATION_JSON)
   public final String getModelDefinitions(@QueryParam("all") boolean all) throws JsonProcessingException {
      if (all) {
         JSONObject data = new JSONObject();
         for (ModelInformation modelMetadata : information.getModelInformations()) {
            // Kombinierte Modelle wie opt-act bestehen aus den Definitionen ihrer Teilmodelle und haben keine eigene Datei.
            if (ScenarioEndpoint.class.getResource("/modeldefinitions/" + modelMetadata.getId() + ".json") == null) {
               LOG.warn("Keine Modelldefinition für Modell {} ({}) vorhanden, wird übersprungen", modelMetadata.getId(), modelMetadata.getName());
               continue;
            }
            final String modelData = readModeldefinition(modelMetadata.getId());
            JSONObject jsonObject = new JSONObject();
            String metadataJSON = Constants.MAPPER.writeValueAsString(modelMetadata); // inefficient, but works
            jsonObject.put("metadata", new JSONObject(metadataJSON)); 
            jsonObject.put("data", new JSONObject(modelData));
            data.put("" + modelMetadata.getId(), jsonObject);
         }
         return data.toString();
      } else {
         return ModelInformations.serializeData(information);
      }
   }

   /**
    * Liefert eine Modelldefinition (inputId), welche Parameter ausblendet da diese vom Output Modell überschrieben werden.
    */
   @ApiOperation(value = "Gibt eine Modelldefinition des inputModele zurück, welche Parameter ausblendet, "
         + "wenn diese durch das iutputModels überschrieben werden."
         , notes = "Sichtbarkeitsänderungen bzgl Ihrer Output-Input-Synergien")
   @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 500, message = "ID nicht vorhanden") })
   @GET
   @Consumes(MediaType.APPLICATION_JSON)
   @Produces(MediaType.APPLICATION_JSON)
   @Path("connectedModelDefinition/{outputId}/{inputId}")
   public final String getConnectedModelDefinitions(@PathParam("outputId") final int outputId, @PathParam("inputId") final int inputId) {

      final String inputContent = readModeldefinition(inputId);
      Map<ConnectionType, SyncableParameter> map = SyncableParameterHandler.getInstance().fetchSyncableParameter(new Pair<>(outputId, inputId));

      try {
         JsonNode tree = mapper.readTree(inputContent);
         for (Map.Entry<ConnectionType, SyncableParameter> entrie : map.entrySet()) {
            if (entrie.getKey().equals(ConnectionType.OUTPUT)) continue;
            Map<DependencyType, List<String>> parameterMap = entrie.getValue().getParameterMap();
            for (Map.Entry<DependencyType, List<String>> entry : parameterMap.entrySet()) {
               JsonNode definitions = tree.findValue("definitions");
               switch (entry.getKey()) {
               case SCALAR: {
                  JsonNode typeNode = definitions.findValue("scalars");
                  for (String value : entry.getValue()) {
                     JsonNode targetNode = typeNode.findValue(value);
                     if (targetNode != null && !targetNode.isNull()) {
                        ((ObjectNode) targetNode).put("hidden", "true");
                     }
                  }
                  break;
               }
               case TABLE_SCALAR:
               case TABLE_TIMESERIES: {
                  JsonNode typeNode = definitions.findValue("tables");
                  for (String value : entry.getValue()) {
                     JsonNode targetNode = typeNode.findValue(value);
                     if (targetNode != null && !targetNode.isNull()) {
                        ((ObjectNode) targetNode).put("hidden", "true");
                     }
                  }
                  break;
               }
               case TIMESERIES: {
                  JsonNode typeNode = definitions.findValue("timeseries");
                  for (String value : entry.getValue()) {
                     JsonNode targetNode = typeNode.findValue(value);
                     if (targetNode != null && !targetNode.isNull()) {
                        ((ObjectNode) targetNode).put("hidden", "true");
                     }
                  }
                  break;
               }
               case SET_TIMESERIES:
               case SET_SCALAR: {
                  JsonNode setsNode = definitions.findValue("sets");
                  for (String value : entry.getValue()) {
                     JsonNode targetNode = traverse(setsNode, value);
                     if (targetNode != null && !targetNode.isNull()) {
                        ((ObjectNode) targetNode).put("hidden", "true");
                     }
                  }
                  break;
               }
               }
            }
         }
         return mapper.writeValueAsString(tree);
      } catch (IOException e) {
         e.printStackTrace();
         return "";
      }
   }

   /**
    * Liest die im Classpath liegende Modelldefinition aus und gibt sie zurück.
    *
    * @param id Die ID des Modells
    * @return Die gewünschte Modelldefinition als String
    * @throws JsonParseException Tritt auf falls Fehler beim parsen von JSON-Daten auftreten
    * @throws JsonMappingException Tritt auf falls Fehler beim Mapping von JSON-Daten auftreten
    * @throws IOException Tritt auf falls Fehler beim Lesen und Schreiben von Dateien auftreten
    */
   @ApiOperation(value = "Gibt die gewünschte Modelldefinition zurück", notes = "Liest die im Classpath liegende Modelldefinition aus und gibt sie zurück.")
   @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 500, message = "ID nicht vorhanden") })
   @GET
   @Produces(MediaType.APPLICATION_JSON)
   @Path("{id}")
   public final String getConcreteModelDefinitions(@PathParam("id") final int id) throws JsonParseException, JsonMappingException, IOException {
      LOG.info("Lade Parameter für: {}", id);
      // final ModelDefinitionMetadata spm = data.get(id);
      final String content = readModeldefinition(id);
      LOG.debug("Gebe zurück: {}", content.substring(0, 100));
      return content;
   }

   private String readModeldefinition(final int id) {
      final InputStream input = ScenarioEndpoint.class.getResourceAsStream("/modeldefinitions/" + id + ".json");
      final Scanner scanner = new Scanner(input, "UTF-8");
      final String content = scanner.useDelimiter("\\Z").next();
      scanner.close();
      return content;
   }

   /**
    * Helper for finding the correct JsonNode within the model definition tree
    * @param root JsonNode
    * @param ref Target
    * @return JsonNode if found or null
    */
   public static JsonNode traverse(JsonNode root, String ref) {
      if (root.isObject()) {
         Iterator<String> fieldNames = root.fieldNames();

         while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode fieldValue = root.get(fieldName);
            // else error-prone
            String fieldText = fieldValue.asText();
            LOG.trace("{} : {}", fieldName, fieldText);
            if (fieldText.contains(ref)) {
               return root;
            }

            JsonNode target = traverse(fieldValue, ref);
            if (target != null && !target.isNull()) {
               return target;
            }
         }
      } else if (root.isArray()) {
         ArrayNode arrayNode = (ArrayNode) root;
         for (int i = 0; i < arrayNode.size(); i++) {
            JsonNode arrayElement = arrayNode.get(i);
            JsonNode target = traverse(arrayElement, ref);
            if (target != null && !target.isNull()) {
               return target;
            }
         }
      }

      return null;
   }
}
