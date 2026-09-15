package de.unileipzig.irpsim.core.security;

import java.util.List;
import java.util.Optional;

import javax.persistence.TypedQuery;

import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManager;
import de.unileipzig.irpsim.core.simulation.data.persistence.ClosableEntityManagerProxy;

/**
 * Verwaltet die Benutzergruppen der Anwendung.
 *
 * @author benligil
 */
public class UserGroupRepository {

   /**
    * Liefert alle angelegten Gruppen.
    *
    * @return Die Gruppen der Anwendung
    */
   public List<UserGroup> findAll() {
      try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
         final TypedQuery<UserGroup> query = em.createQuery("SELECT g FROM UserGroup g ORDER BY g.name", UserGroup.class);
         return query.getResultList();
      }
   }

   /**
    * Sucht eine Gruppe anhand ihres Namens.
    *
    * @param name Der Name der Gruppe
    * @return Die Gruppe, falls sie existiert
    */
   public Optional<UserGroup> findByName(final String name) {
      try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
         final TypedQuery<UserGroup> query = em.createQuery("SELECT g FROM UserGroup g WHERE g.name = :name", UserGroup.class);
         query.setParameter("name", name);
         return query.getResultList().stream().findFirst();
      }
   }

   /**
    * Legt eine Gruppe an oder aktualisiert sie.
    *
    * @param group Die zu speichernde Gruppe
    * @return Die gespeicherte Gruppe
    */
   public UserGroup save(final UserGroup group) {
      try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
         em.getTransaction().begin();
         final UserGroup merged = em.merge(group);
         em.getTransaction().commit();
         return merged;
      }
   }

   /**
    * Löscht eine Gruppe samt ihrer Mitgliedschaften.
    *
    * @param name Der Name der Gruppe
    * @return true, falls eine Gruppe gelöscht wurde
    */
   public boolean deleteByName(final String name) {
      try (ClosableEntityManager em = ClosableEntityManagerProxy.newInstance()) {
         em.getTransaction().begin();
         final TypedQuery<UserGroup> query = em.createQuery("SELECT g FROM UserGroup g WHERE g.name = :name", UserGroup.class);
         query.setParameter("name", name);
         final Optional<UserGroup> group = query.getResultList().stream().findFirst();
         group.ifPresent(em::remove);
         em.getTransaction().commit();
         return group.isPresent();
      }
   }
}
