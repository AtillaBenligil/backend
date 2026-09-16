package de.unileipzig.irpsim.server.security;

import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Anbindung an das LDAP-Verzeichnis über JNDI.
 *
 * Die Anmeldung erfolgt über einen Bind-Vorgang mit den Zugangsdaten des
 * Benutzers. Das Backend hält dadurch selbst keine Passwörter vor; die Prüfung
 * findet ausschließlich im Verzeichnis statt. Nach erfolgreicher Anmeldung
 * werden die Gruppen des Benutzers ermittelt, da die Rechteprüfung der
 * Szenarien und Simulationen auf diesen Gruppen aufsetzt.
 *
 * @author benligil
 */
public final class JndiLdapAuthenticator implements LdapAuthenticator {

   private static final Logger LOG = LogManager.getLogger(JndiLdapAuthenticator.class);

   private static final String CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
   private static final String PASSWORD_ATTRIBUTE = "userPassword";
   private static final String GROUP_NAME_ATTRIBUTE = "cn";
   private static final String GROUP_MEMBER_FILTER = "(member={0})";

   private final LdapConfiguration configuration;

   /**
    * Erzeugt eine Verzeichnisanbindung für die übergebene Konfiguration.
    *
    * @param configuration Die Verbindungsdaten des Verzeichnisses
    */
   public JndiLdapAuthenticator(final LdapConfiguration configuration) {
      this.configuration = configuration;
   }

   @Override
   public AuthenticatedUser authenticate(final String username, final String password) throws AuthenticationException {
      requireCredentials(username, password);

      final String userDn = configuration.getUserDn(username);
      DirContext context = null;
      try {
         context = bind(userDn, password);
         final Set<String> groups = readGroups(context, userDn);
         LOG.info("Benutzer {} erfolgreich angemeldet, Gruppen: {}", username, groups);
         return new AuthenticatedUser(username, groups);
      } catch (final NamingException e) {
         LOG.debug("Anmeldung für {} fehlgeschlagen", username, e);
         throw new AuthenticationException("Anmeldung fehlgeschlagen", e);
      } finally {
         close(context);
      }
   }

   @Override
   public void changePassword(final String username, final String oldPassword, final String newPassword) throws AuthenticationException {
      requireCredentials(username, oldPassword);
      if (newPassword == null || newPassword.isEmpty()) {
         throw new AuthenticationException("Das neue Passwort darf nicht leer sein");
      }

      final String userDn = configuration.getUserDn(username);
      DirContext context = null;
      try {
         // Der Bind-Vorgang stellt sicher, dass das bisherige Passwort bekannt ist.
         context = bind(userDn, oldPassword);
         final ModificationItem[] modifications = new ModificationItem[] {
               new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute(PASSWORD_ATTRIBUTE, newPassword))
         };
         context.modifyAttributes(userDn, modifications);
         LOG.info("Passwort für Benutzer {} geändert", username);
      } catch (final NamingException e) {
         LOG.debug("Passwortänderung für {} fehlgeschlagen", username, e);
         throw new AuthenticationException("Passwortänderung fehlgeschlagen", e);
      } finally {
         close(context);
      }
   }

   /**
    * Baut eine authentifizierte Verbindung zum Verzeichnis auf.
    *
    * @param userDn Der DN des anzumeldenden Benutzers
    * @param password Das Passwort des Benutzers
    * @return Der gebundene Verzeichniskontext
    * @throws NamingException Falls die Anmeldung abgelehnt wird
    */
   private DirContext bind(final String userDn, final String password) throws NamingException {
      final Hashtable<String, String> environment = new Hashtable<>();
      environment.put(Context.INITIAL_CONTEXT_FACTORY, CONTEXT_FACTORY);
      environment.put(Context.PROVIDER_URL, configuration.getUrl());
      environment.put(Context.SECURITY_AUTHENTICATION, "simple");
      environment.put(Context.SECURITY_PRINCIPAL, userDn);
      environment.put(Context.SECURITY_CREDENTIALS, password);
      return new InitialDirContext(environment);
   }

   /**
    * Ermittelt die Gruppen, in denen der Benutzer als Mitglied eingetragen ist.
    *
    * @param context Der gebundene Verzeichniskontext
    * @param userDn Der DN des Benutzers
    * @return Die Namen der Gruppen des Benutzers
    * @throws NamingException Falls die Suche fehlschlägt
    */
   private Set<String> readGroups(final DirContext context, final String userDn) throws NamingException {
      final Set<String> groups = new LinkedHashSet<>();
      final SearchControls controls = new SearchControls();
      controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
      controls.setReturningAttributes(new String[] { GROUP_NAME_ATTRIBUTE });

      final NamingEnumeration<SearchResult> results = context.search(configuration.getGroupSearchBase(), GROUP_MEMBER_FILTER, new Object[] { userDn }, controls);
      try {
         while (results.hasMore()) {
            final Attributes attributes = results.next().getAttributes();
            final Attribute name = attributes.get(GROUP_NAME_ATTRIBUTE);
            if (name != null && name.get() != null) {
               groups.add(name.get().toString());
            }
         }
      } finally {
         results.close();
      }
      return groups;
   }

   private static void requireCredentials(final String username, final String password) throws AuthenticationException {
      if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
         throw new AuthenticationException("Benutzername und Passwort dürfen nicht leer sein");
      }
   }

   private static void close(final DirContext context) {
      if (context != null) {
         try {
            context.close();
         } catch (final NamingException e) {
            LOG.warn("Verzeichniskontext konnte nicht geschlossen werden", e);
         }
      }
   }
}
