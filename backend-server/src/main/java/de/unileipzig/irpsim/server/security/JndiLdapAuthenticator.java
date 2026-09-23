package de.unileipzig.irpsim.server.security;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
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
 * Ist ein Dienstkonto konfiguriert, werden die Gruppen unter diesem gelesen
 * und nicht unter dem Benutzer selbst. OpenLDAP erlaubt normalen Benutzern in
 * der Standardeinstellung nur den eigenen Eintrag; die Gruppensuche würde dort
 * scheitern und damit jede Anmeldung verhindern.
 *
 * @author benligil
 */
public final class JndiLdapAuthenticator implements LdapAuthenticator {

   private static final Logger LOG = LogManager.getLogger(JndiLdapAuthenticator.class);

   private static final String CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
   private static final String PASSWORD_ATTRIBUTE = "userPassword";
   private static final String GROUP_NAME_ATTRIBUTE = "cn";
   private static final String GROUP_MEMBER_FILTER = "(member={0})";
   private static final String USER_NAME_ATTRIBUTE = "uid";
   private static final String USER_MAIL_FILTER = "(mail={0})";

   private final LdapConfiguration configuration;

   /**
    * Erzeugt eine Verzeichnisanbindung für die übergebene Konfiguration.
    *
    * @param configuration Die Verbindungsdaten des Verzeichnisses
    */
   public JndiLdapAuthenticator(final LdapConfiguration configuration) {
      this.configuration = configuration;
      if (configuration.hasServiceAccount()) {
         LOG.info("Gruppensuche über das Dienstkonto {}", configuration.getBindDn());
      } else {
         LOG.warn("Kein Dienstkonto konfiguriert ({}, {}); die Gruppen werden unter dem angemeldeten Benutzer gelesen",
               LdapConfiguration.ENV_BIND_DN, LdapConfiguration.ENV_BIND_PASSWORD);
      }
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

   @Override
   public Optional<String> findUsernameByMail(final String mail) throws AuthenticationException {
      if (mail == null || mail.trim().isEmpty()) {
         return Optional.empty();
      }
      if (!configuration.hasServiceAccount()) {
         throw new AuthenticationException("Ohne Dienstkonto kann das Verzeichnis nicht durchsucht werden");
      }
      DirContext context = null;
      try {
         context = bindServiceAccount();
         final SearchControls controls = new SearchControls();
         controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
         controls.setReturningAttributes(new String[] { USER_NAME_ATTRIBUTE });
         final List<String> usernames = new ArrayList<>();
         final NamingEnumeration<SearchResult> results = context.search(configuration.getUserSearchBase(), USER_MAIL_FILTER,
               new Object[] { mail.trim() }, controls);
         try {
            while (results.hasMore()) {
               final Attribute uid = results.next().getAttributes().get(USER_NAME_ATTRIBUTE);
               if (uid != null && uid.get() != null) {
                  usernames.add(uid.get().toString());
               }
            }
         } finally {
            results.close();
         }
         if (usernames.size() > 1) {
            // Bei mehrdeutigen Adressen wird bewusst niemandem ein Recht eingeräumt.
            LOG.warn("E-Mail-Adresse {} ist mehreren Benutzern zugeordnet: {}", mail, usernames);
            return Optional.empty();
         }
         return usernames.stream().findFirst();
      } catch (final NamingException e) {
         throw new AuthenticationException("Suche nach " + mail + " fehlgeschlagen", e);
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
    * Ist ein Dienstkonto konfiguriert, wird die Suche unter diesem ausgeführt,
    * andernfalls unter dem bereits angemeldeten Benutzer.
    *
    * @param userContext Der mit den Zugangsdaten des Benutzers gebundene Kontext
    * @param userDn Der DN des Benutzers
    * @return Die Namen der Gruppen des Benutzers
    * @throws NamingException Falls die Suche fehlschlägt
    */
   private Set<String> readGroups(final DirContext userContext, final String userDn) throws NamingException {
      if (!configuration.hasServiceAccount()) {
         return searchGroups(userContext, userDn);
      }
      DirContext serviceContext = null;
      try {
         serviceContext = bindServiceAccount();
         return searchGroups(serviceContext, userDn);
      } finally {
         close(serviceContext);
      }
   }

   /**
    * Meldet das Dienstkonto am Verzeichnis an.
    *
    * Ein Fehlschlag ist hier ein Konfigurationsfehler und kein falsches
    * Benutzerpasswort, daher wird er als Fehler protokolliert.
    *
    * @return Der mit dem Dienstkonto gebundene Kontext
    * @throws NamingException Falls das Dienstkonto abgewiesen wird
    */
   private DirContext bindServiceAccount() throws NamingException {
      try {
         return bind(configuration.getBindDn(), configuration.getBindPassword());
      } catch (final NamingException e) {
         LOG.error("Das Dienstkonto {} wurde vom Verzeichnis abgewiesen; {} und {} prüfen", configuration.getBindDn(),
               LdapConfiguration.ENV_BIND_DN, LdapConfiguration.ENV_BIND_PASSWORD);
         throw e;
      }
   }

   /**
    * Sucht die Gruppen, in denen der Benutzer als Mitglied eingetragen ist.
    *
    * @param context Der gebundene Verzeichniskontext, unter dem gesucht wird
    * @param userDn Der DN des Benutzers
    * @return Die Namen der Gruppen des Benutzers
    * @throws NamingException Falls die Suche fehlschlägt
    */
   private Set<String> searchGroups(final DirContext context, final String userDn) throws NamingException {
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
