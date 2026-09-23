package de.unileipzig.irpsim.core.security;

/**
 * Die Arten von Ressourcen, deren Sichtbarkeit über Zugriffsrechte gesteuert
 * wird.
 *
 * @author benligil
 */
public enum ResourceType {

   /** Ein Szenario der Simulationsverwaltung. */
   SCENARIO,

   /** Ein Simulationsauftrag der Optimierung. */
   JOB,

   /**
    * Ein Stammdatum der Datenverwaltung. Ersetzt die bisherige Zuordnung von
    * verantwortlichen Personen.
    */
   STAMMDATUM
}
