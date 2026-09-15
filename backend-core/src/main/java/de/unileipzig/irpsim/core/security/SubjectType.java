package de.unileipzig.irpsim.core.security;

/**
 * Gibt an, ob ein Zugriffsrecht einem einzelnen Benutzer oder einer Gruppe
 * eingeräumt wurde.
 *
 * @author benligil
 */
public enum SubjectType {

   /** Das Recht gilt für einen einzelnen Benutzer. */
   USER,

   /** Das Recht gilt für alle Mitglieder einer Gruppe. */
   GROUP
}
