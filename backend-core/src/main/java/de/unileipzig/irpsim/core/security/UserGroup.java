package de.unileipzig.irpsim.core.security;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Table;

/**
 * Eine Benutzergruppe der Anwendung.
 *
 * Die Gruppen werden in der Datenbank des Backends verwaltet und nicht im
 * LDAP-Verzeichnis, da sie eine fachliche Einteilung der Anwendung abbilden
 * und ohne Schreibzugriff auf das Verzeichnis der Universität gepflegt werden
 * können. Die Mitglieder werden über ihren LDAP-Anmeldenamen referenziert, so
 * dass Verzeichnis und Anwendung dieselbe Identität verwenden.
 *
 * @author benligil
 */
@Entity
@Table(name = "usergroup")
public class UserGroup implements Serializable {

   private static final long serialVersionUID = 1L;

   @Id
   @GeneratedValue(strategy = GenerationType.TABLE)
   private int id;

   @Column(unique = true, nullable = false)
   private String name;

   private String description;

   @ElementCollection(fetch = FetchType.EAGER)
   @CollectionTable(name = "usergroup_member", joinColumns = @JoinColumn(name = "group_id"))
   @Column(name = "username")
   private Set<String> members = new LinkedHashSet<>();

   /**
    * Parameterloser Konstruktor für JPA und die JSON-Deserialisierung.
    */
   public UserGroup() {

   }

   /**
    * Erzeugt eine Gruppe mit Name und Beschreibung.
    *
    * @param name Der eindeutige Name der Gruppe
    * @param description Die Beschreibung der Gruppe
    */
   public UserGroup(final String name, final String description) {
      this.name = name;
      this.description = description;
   }

   public int getId() {
      return id;
   }

   public void setId(final int id) {
      this.id = id;
   }

   public String getName() {
      return name;
   }

   public void setName(final String name) {
      this.name = name;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(final String description) {
      this.description = description;
   }

   public Set<String> getMembers() {
      return members;
   }

   public void setMembers(final Set<String> members) {
      this.members = members;
   }

   /**
    * Nimmt einen Benutzer in die Gruppe auf.
    *
    * @param username Der Anmeldename des Benutzers
    * @return true, falls der Benutzer noch nicht Mitglied war
    */
   public boolean addMember(final String username) {
      return members.add(username);
   }

   /**
    * Entfernt einen Benutzer aus der Gruppe.
    *
    * @param username Der Anmeldename des Benutzers
    * @return true, falls der Benutzer Mitglied war
    */
   public boolean removeMember(final String username) {
      return members.remove(username);
   }
}
