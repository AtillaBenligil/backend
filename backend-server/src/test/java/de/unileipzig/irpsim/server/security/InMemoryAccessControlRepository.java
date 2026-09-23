package de.unileipzig.irpsim.server.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.unileipzig.irpsim.core.security.AccessControlEntry;
import de.unileipzig.irpsim.core.security.AccessControlRepository;
import de.unileipzig.irpsim.core.security.Permission;
import de.unileipzig.irpsim.core.security.ResourceType;
import de.unileipzig.irpsim.core.security.SubjectType;

/**
 * Eine Rechteablage im Arbeitsspeicher für die Tests.
 *
 * Damit lässt sich die Entscheidungslogik der Rechteverwaltung unabhängig von
 * der Datenbank prüfen.
 *
 * @author benligil
 */
final class InMemoryAccessControlRepository implements AccessControlRepository {


   private final List<AccessControlEntry> entries = new ArrayList<>();
   private final Map<String, Set<String>> memberships = new LinkedHashMap<>();

   void addGroupMembership(final String username, final String group) {
      memberships.computeIfAbsent(username, key -> new LinkedHashSet<>()).add(group);
   }

   @Override
   public List<AccessControlEntry> findEntries(final ResourceType resourceType, final long resourceId) {
      return entries.stream()
            .filter(entry -> entry.getResourceType() == resourceType && entry.getResourceId() == resourceId)
            .collect(Collectors.toList());
   }

   @Override
   public Set<Long> findRestrictedResources(final ResourceType resourceType) {
      return entries.stream()
            .filter(entry -> entry.getResourceType() == resourceType)
            .map(AccessControlEntry::getResourceId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
   }

   @Override
   public Set<Long> findPermittedResources(final ResourceType resourceType, final String username, final Set<String> groups,
         final Permission required) {
      return entries.stream()
            .filter(entry -> entry.getResourceType() == resourceType)
            .filter(entry -> entry.getPermission().includes(required))
            .filter(entry -> entry.getSubjectType() == SubjectType.USER
                  ? entry.getSubjectName().equals(username)
                  : groups.contains(entry.getSubjectName()))
            .map(AccessControlEntry::getResourceId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
   }

   @Override
   public Set<String> findGroupNames(final String username) {
      return memberships.getOrDefault(username, Collections.emptySet());
   }

   @Override
   public void save(final AccessControlEntry entry) {
      entries.add(entry);
   }

   @Override
   public boolean deleteEntry(final ResourceType resourceType, final long resourceId, final SubjectType subjectType,
         final String subjectName) {
      return entries.removeIf(entry -> entry.getResourceType() == resourceType && entry.getResourceId() == resourceId
            && entry.getSubjectType() == subjectType && entry.getSubjectName().equals(subjectName));
   }

   @Override
   public void deleteEntries(final ResourceType resourceType, final long resourceId) {
      entries.removeIf(entry -> entry.getResourceType() == resourceType && entry.getResourceId() == resourceId);
   }
}
