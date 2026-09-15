package de.unileipzig.irpsim.core.security;

/**
 * Die Rechte, die einem Benutzer oder einer Gruppe an einer Ressource
 * eingeräumt werden können.
 *
 * Das Schreibrecht schließt das Leserecht ein, damit für eine bearbeitbare
 * Ressource nicht zwei Einträge gepflegt werden müssen.
 *
 * @author benligil
 */
public enum Permission {

   /** Die Ressource darf gelesen werden. */
   READ,

   /** Die Ressource darf gelesen und verändert werden. */
   WRITE;

   /**
    * Prüft, ob dieses Recht das geforderte Recht einschließt.
    *
    * @param required Das geforderte Recht
    * @return true, falls das Recht ausreicht
    */
   public boolean includes(final Permission required) {
      return this == WRITE || this == required;
   }
}
