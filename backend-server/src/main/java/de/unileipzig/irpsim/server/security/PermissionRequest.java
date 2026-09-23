package de.unileipzig.irpsim.server.security;

import de.unileipzig.irpsim.core.security.Permission;
import de.unileipzig.irpsim.core.security.SubjectType;

/**
 * Die Daten einer Anfrage, einem Benutzer oder einer Gruppe ein Recht an
 * einer Ressource einzuräumen.
 *
 * @author benligil
 */
public final class PermissionRequest {

   private SubjectType subjectType;
   private String subjectName;
   private Permission permission;

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
