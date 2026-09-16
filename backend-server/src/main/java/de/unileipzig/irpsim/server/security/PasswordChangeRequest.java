package de.unileipzig.irpsim.server.security;

/**
 * Die Daten einer Anfrage zur Passwortänderung.
 *
 * @author benligil
 */
public final class PasswordChangeRequest {

   private String oldPassword;
   private String newPassword;

   public String getOldPassword() {
      return oldPassword;
   }

   public void setOldPassword(final String oldPassword) {
      this.oldPassword = oldPassword;
   }

   public String getNewPassword() {
      return newPassword;
   }

   public void setNewPassword(final String newPassword) {
      this.newPassword = newPassword;
   }
}
