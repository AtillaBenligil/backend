package de.unileipzig.irpsim.server.security;

/**
 * Bündelt die Verbindungsdaten des LDAP-Verzeichnisses.
 *
 * Die Werte werden wie die übrigen Betriebsparameter des Backends aus
 * Umgebungsvariablen gelesen, damit die Konfiguration zwischen
 * Entwicklungs- und Produktivumgebung ohne Codeänderung wechseln kann.
 *
 * Optional kann ein Dienstkonto angegeben werden, unter dem die Gruppen eines
 * Benutzers gelesen werden. Das ist notwendig, sobald das Verzeichnis --
 * wie OpenLDAP in der Standardeinstellung -- normalen Benutzern nur den
 * eigenen Eintrag zeigt. Das Passwort des Benutzers wird davon unabhängig
 * weiterhin über einen Bind mit seinen eigenen Zugangsdaten geprüft.
 *
 * @author benligil
 */
public final class LdapConfiguration {

   public static final String ENV_URL = "IRPSIM_LDAP_URL";
   public static final String ENV_BASE_DN = "IRPSIM_LDAP_BASE_DN";
   public static final String ENV_USER_OU = "IRPSIM_LDAP_USER_OU";
   public static final String ENV_GROUP_OU = "IRPSIM_LDAP_GROUP_OU";
   public static final String ENV_BIND_DN = "IRPSIM_LDAP_BIND_DN";
   public static final String ENV_BIND_PASSWORD = "IRPSIM_LDAP_BIND_PASSWORD";

   private static final String DEFAULT_URL = "ldap://localhost:10389";
   private static final String DEFAULT_BASE_DN = "dc=irpsim,dc=uni-leipzig,dc=de";
   private static final String DEFAULT_USER_OU = "ou=people";
   private static final String DEFAULT_GROUP_OU = "ou=groups";

   private final String url;
   private final String baseDn;
   private final String userOu;
   private final String groupOu;
   private final String bindDn;
   private final String bindPassword;

   /**
    * Erzeugt eine Konfiguration ohne Dienstkonto.
    *
    * Die Gruppen werden dann mit den Zugangsdaten des angemeldeten Benutzers
    * gelesen; das setzt voraus, dass das Verzeichnis dies erlaubt.
    *
    * @param url Die URL des LDAP-Servers
    * @param baseDn Der Basis-DN des Verzeichnisses
    * @param userOu Die Organisationseinheit der Benutzer
    * @param groupOu Die Organisationseinheit der Gruppen
    */
   public LdapConfiguration(final String url, final String baseDn, final String userOu, final String groupOu) {
      this(url, baseDn, userOu, groupOu, null, null);
   }

   /**
    * Erzeugt eine Konfiguration mit Dienstkonto für die Gruppensuche.
    *
    * @param url Die URL des LDAP-Servers
    * @param baseDn Der Basis-DN des Verzeichnisses
    * @param userOu Die Organisationseinheit der Benutzer
    * @param groupOu Die Organisationseinheit der Gruppen
    * @param bindDn Der DN des lesenden Dienstkontos oder null
    * @param bindPassword Das Passwort des Dienstkontos oder null
    */
   public LdapConfiguration(final String url, final String baseDn, final String userOu, final String groupOu,
         final String bindDn, final String bindPassword) {
      this.url = url;
      this.baseDn = baseDn;
      this.userOu = userOu;
      this.groupOu = groupOu;
      this.bindDn = bindDn;
      this.bindPassword = bindPassword;
   }

   /**
    * Liest die Konfiguration aus den Umgebungsvariablen und fällt auf die
    * Standardwerte der lokalen Entwicklungsumgebung zurück.
    *
    * Für das Dienstkonto gibt es bewusst keinen Standardwert, damit kein
    * Passwort im Quelltext steht.
    *
    * @return Die Konfiguration des LDAP-Verzeichnisses
    */
   public static LdapConfiguration fromEnvironment() {
      return new LdapConfiguration(
            valueOrDefault(ENV_URL, DEFAULT_URL),
            valueOrDefault(ENV_BASE_DN, DEFAULT_BASE_DN),
            valueOrDefault(ENV_USER_OU, DEFAULT_USER_OU),
            valueOrDefault(ENV_GROUP_OU, DEFAULT_GROUP_OU),
            valueOrDefault(ENV_BIND_DN, null),
            valueOrDefault(ENV_BIND_PASSWORD, null));
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
    * Liefert den DN, unterhalb dessen die Benutzer abgelegt sind.
    *
    * @return Der DN der Benutzer-Organisationseinheit
    */
   public String getUserSearchBase() {
      return userOu + "," + baseDn;
   }

   /**
    * Liefert den DN, unterhalb dessen die Gruppen abgelegt sind.
    *
    * @return Der DN der Gruppen-Organisationseinheit
    */
   public String getGroupSearchBase() {
      return groupOu + "," + baseDn;
   }

   /**
    * Prüft, ob für die Gruppensuche ein Dienstkonto konfiguriert ist.
    *
    * @return true, falls DN und Passwort des Dienstkontos gesetzt sind
    */
   public boolean hasServiceAccount() {
      return bindDn != null && !bindDn.isEmpty() && bindPassword != null && !bindPassword.isEmpty();
   }

   public String getBindDn() {
      return bindDn;
   }

   public String getBindPassword() {
      return bindPassword;
   }
}
