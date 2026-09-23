package de.unileipzig.irpsim.core.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.TypedQuery;

import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManager;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManagerProxy;

/**
 * Persistiert Zugriffsrechte und Gruppen über JPA.
 *
 * Die Klasse nutzt denselben {@link ClosableEntityManagerProxy} wie die
 * übrigen Datenzugriffe des Backends, damit die Rechteverwaltung dieselbe
 * Verbindungsverwaltung verwendet wie die bestehenden Entitäten.
 *
 * @author benligil
 */
public class JpaAccessControlRepository implements AccessControlRepository {

   @Override
   public List<AccessControlEntry> findEntries(final ResourceType resourceType, final long resourceId) {
      try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
         final TypedQuery<AccessControlEntry> query = em.createQuery(
               "SELECT e FROM AccessControlEntry e WHERE e.resourceType = :type AND e.resourceId = :id", AccessControlEntry.class);
         query.setParameter("type", resourceType);
         query.setParameter("id", resourceId);
         return query.getResultList();
      }
   }

   @Override
   public Set<Long> findRestrictedResources(final ResourceType resourceType) {
      try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
         final TypedQuery<Long> query = em.createQuery(
               "SELECT DISTINCT e.resourceId FROM AccessControlEntry e WHERE e.resourceType = :type", Long.class);
         query.setParameter("type", resourceType);
         return new LinkedHashSet<>(query.getResultList());
      }
   }

   @Override
   public Set<Long> findPermittedResources(final ResourceType resourceType, final String username, final Set<String> groups, final Permission required) {
      try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
         final TypedQuery<AccessControlEntry> query = em.createQuery(
               "SELECT e FROM AccessControlEntry e WHERE e.resourceType = :type", AccessControlEntry.class);
         query.setParameter("type", resourceType);
         return query.getResultList().stream()
               .filter(entry -> entry.getPermission().includes(required))
               .filter(entry -> matches(entry, username, groups))
               .map(AccessControlEntry::getResourceId)
               .collect(Collectors.toCollection(LinkedHashSet::new));
      }
   }

   @Override
   public Set<String> findGroupNames(final String username) {
      try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
         final TypedQuery<String> query = em.createQuery(
               "SELECT g.name FROM UserGroup g JOIN g.members m WHERE m = :username", String.class);
         query.setParameter("username", username);
         return new LinkedHashSet<>(query.getResultList());
      } catch (final RuntimeException e) {
         // Solange keine Gruppe angelegt wurde, existiert die Tabelle unter
         // Umstaenden noch nicht; der Benutzer hat dann schlicht keine Gruppen.
         return Collections.emptySet();
      }
   }

   @Override
   public void save(final AccessControlEntry entry) {
      try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
         em.getTransaction().begin();
         em.persist(entry);
         em.getTransaction().commit();
      }
   }

   @Override
   public boolean deleteEntry(final ResourceType resourceType, final long resourceId, final SubjectType subjectType, final String subjectName) {
      try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
         em.getTransaction().begin();
         final int removed = em.createQuery("DELETE FROM AccessControlEntry e WHERE e.resourceType = :type AND e.resourceId = :id "
               + "AND e.subjectType = :subjectType AND e.subjectName = :subjectName")
               .setParameter("type", resourceType)
               .setParameter("id", resourceId)
               .setParameter("subjectType", subjectType)
               .setParameter("subjectName", subjectName)
               .executeUpdate();
         em.getTransaction().commit();
         return removed > 0;
      }
   }

   @Override
   public void deleteEntries(final ResourceType resourceType, final long resourceId) {
      try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
         em.getTransaction().begin();
         em.createQuery("DELETE FROM AccessControlEntry e WHERE e.resourceType = :type AND e.resourceId = :id")
               .setParameter("type", resourceType)
               .setParameter("id", resourceId)
               .executeUpdate();
         em.getTransaction().commit();
      }
   }

   /**
    * Prüft, ob ein Eintrag auf den Benutzer oder eine seiner Gruppen zutrifft.
    *
    * @param entry Der zu prüfende Eintrag
    * @param username Der Anmeldename des Benutzers
    * @param groups Die Gruppen des Benutzers
    * @return true, falls der Eintrag für den Benutzer gilt
    */
   private static boolean matches(final AccessControlEntry entry, final String username, final Set<String> groups) {
      if (entry.getSubjectType() == SubjectType.USER) {
         return entry.getSubjectName().equals(username);
      }
      return groups.contains(entry.getSubjectName());
   }
}
