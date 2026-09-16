package de.unileipzig.irpsim.server.security;

/**
 * Bündelt die Verbindungsdaten des LDAP-Verzeichnisses.
 *
 * Die Werte werden wie die übrigen Betriebsparameter des Backends aus
 * Umgebungsvariablen gelesen, damit die Konfiguration zwischen
 * Entwicklungs- und Produktivumgebung ohne Codeänderung wechseln kann.
 *
 * @author benligil
 */
public final class LdapConfiguration {

   public static final String ENV_URL = "IRPSIM_LDAP_URL";
   public static final String ENV_BASE_DN = "IRPSIM_LDAP_BASE_DN";
   public static final String ENV_USER_OU = "IRPSIM_LDAP_USER_OU";
   public static final String ENV_GROUP_OU = "IRPSIM_LDAP_GROUP_OU";

   private static final String DEFAULT_URL = "ldap://localhost:10389";
   private static final String DEFAULT_BASE_DN = "dc=irpsim,dc=uni-leipzig,dc=de";
   private static final String DEFAULT_USER_OU = "ou=people";
   private static final String DEFAULT_GROUP_OU = "ou=groups";

   private final String url;
   private final String baseDn;
   private final String userOu;
   private final String groupOu;

   /**
    * Erzeugt eine Konfiguration mit explizit gesetzten Werten.
    *
    * @param url Die URL des LDAP-Servers
    * @param baseDn Der Basis-DN des Verzeichnisses
    * @param userOu Die Organisationseinheit der Benutzer
    * @param groupOu Die Organisationseinheit der Gruppen
    */
   public LdapConfiguration(final String url, final String baseDn, final String userOu, final String groupOu) {
      this.url = url;
      this.baseDn = baseDn;
      this.userOu = userOu;
      this.groupOu = groupOu;
   }

   /**
    * Liest die Konfiguration aus den Umgebungsvariablen und fällt auf die
    * Standardwerte der lokalen Entwicklungsumgebung zurück.
    *
    * @return Die Konfiguration des LDAP-Verzeichnisses
    */
   public static LdapConfiguration fromEnvironment() {
      return new LdapConfiguration(
            valueOrDefault(ENV_URL, DEFAULT_URL),
            valueOrDefault(ENV_BASE_DN, DEFAULT_BASE_DN),
            valueOrDefault(ENV_USER_OU, DEFAULT_USER_OU),
            valueOrDefault(ENV_GROUP_OU, DEFAULT_GROUP_OU));
   }

   private static String valueOrDefault(final String name, final String fallback) {
      final String value = System.getenv(name);
      return value != null && !value.isEmpty() ? value : fallback;
   }

   public String getUrl() {
      return url;
   }

   public String getBaseDn() {
      return baseDn;
   }

   /**
    * Bildet den vollständigen DN eines Benutzers.
    *
    * @param username Der Anmeldename des Benutzers
    * @return Der DN des Benutzereintrags im Verzeichnis
    */
   public String getUserDn(final String username) {
      return "uid=" + username + "," + userOu + "," + baseDn;
   }

   /**
    * Liefert den DN, unterhalb dessen die Gruppen abgelegt sind.
    *
    * @return Der DN der Gruppen-Organisationseinheit
    */
   public String getGroupSearchBase() {
      return groupOu + "," + baseDn;
   }
}
