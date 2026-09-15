package de.unileipzig.irpsim.core.security;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;

/**
 * Ein einzelnes Zugriffsrecht an einer Ressource.
 *
 * Ein Eintrag verbindet eine Ressource -- ein Szenario oder einen
 * Simulationsauftrag -- mit einem Benutzer oder einer Gruppe und dem
 * eingeräumten Recht. Die Rechte werden als eigene Tabelle geführt statt als
 * Feld an der Ressource, damit einer Ressource beliebig viele Benutzer und
 * Gruppen zugeordnet werden können, ohne die bestehenden Entitäten zu ändern.
 *
 * @author benligil
 */
@Entity
@Table(name = "accesscontrolentry", indexes = { @Index(name = "idx_ace_resource", columnList = "resourceType,resourceId") })
public class AccessControlEntry implements Serializable {

   private static final long serialVersionUID = 1L;

   @Id
   @GeneratedValue(strategy = GenerationType.TABLE)
   private int id;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false)
   private ResourceType resourceType;

   @Column(nullable = false)
   private long resourceId;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false)
   private SubjectType subjectType;

   /** Der Anmeldename des Benutzers beziehungsweise der Name der Gruppe. */
   @Column(nullable = false)
   private String subjectName;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false)
   private Permission permission;

   /**
    * Parameterloser Konstruktor für JPA und die JSON-Deserialisierung.
    */
   public AccessControlEntry() {

   }

   /**
    * Erzeugt ein Zugriffsrecht.
    *
    * @param resourceType Die Art der Ressource
    * @param resourceId Die Kennung der Ressource
    * @param subjectType Ob das Recht für einen Benutzer oder eine Gruppe gilt
    * @param subjectName Der Name des Benutzers oder der Gruppe
    * @param permission Das eingeräumte Recht
    */
   public AccessControlEntry(final ResourceType resourceType, final long resourceId, final SubjectType subjectType, final String subjectName,
         final Permission permission) {
      this.resourceType = resourceType;
      this.resourceId = resourceId;
      this.subjectType = subjectType;
      this.subjectName = subjectName;
      this.permission = permission;
   }

   public int getId() {
      return id;
   }

   public void setId(final int id) {
      this.id = id;
   }

   public ResourceType getResourceType() {
      return resourceType;
   }

   public void setResourceType(final ResourceType resourceType) {
      this.resourceType = resourceType;
   }

   public long getResourceId() {
      return resourceId;
   }

   public void setResourceId(final long resourceId) {
      this.resourceId = resourceId;
   }

   public SubjectType getSubjectType() {
      return subjectType;
   }

   public void setSubjectType(final SubjectType subjectType) {
      this.subjectType = subjectType;
   }

   public String getSubjectName() {
      return subjectName;
   }

   public void setSubjectName(final String subjectName) {
      this.subjectName = subjectName;
   }

   public Permission getPermission() {
      return permission;
   }

   public void setPermission(final Permission permission) {
      this.permission = permission;
   }
}
