package de.unileipzig.irpsim.server.security;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

/**
 * Prüft bei jeder Anfrage das mitgesendete Zugriffstoken.
 *
 * Der Filter löst das Token über die Sitzungsverwaltung in den angemeldeten
 * Benutzer auf und hinterlegt ihn im {@link SecurityContext}. Die Endpunkte
 * greifen anschließend über den Sicherheitskontext auf den Benutzer zu und
 * müssen die Anmeldung nicht selbst auswerten. Anfragen ohne gültiges Token
 * werden mit dem Status 401 abgewiesen, sofern der Pfad nicht ausdrücklich
 * freigegeben ist.
 *
 * @author benligil
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public final class AuthenticationFilter implements ContainerRequestFilter {

   public static final String AUTHORIZATION_HEADER = "Authorization";
   public static final String TOKEN_PREFIX = "Bearer ";

   /**
    * Pfade, die ohne Anmeldung erreichbar sein müssen: die Anmeldung selbst,
    * die Versionsinformationen für den Startbildschirm sowie die
    * Schnittstellendokumentation.
    */
   private static final List<String> PUBLIC_PATHS = Arrays.asList("auth/login", "generalinformation", "swagger");

   @Override
   public void filter(final ContainerRequestContext requestContext) throws IOException {
      final String path = requestContext.getUriInfo().getPath();
      if (isPublic(path)) {
         return;
      }

      final Optional<AuthenticatedUser> user = SecurityComponents.getSessionManager().resolve(readToken(requestContext));
      if (!user.isPresent()) {
         requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
               .entity("{\"error\":\"Anmeldung erforderlich\"}")
               .type("application/json;charset=UTF-8")
               .build());
         return;
      }

      requestContext.setSecurityContext(createSecurityContext(user.get(), requestContext.getSecurityContext()));
   }

   /**
    * Liest das Zugriffstoken aus dem Authorization-Header.
    *
    * @param requestContext Der Kontext der Anfrage
    * @return Das Token oder null, falls keines mitgesendet wurde
    */
   private static String readToken(final ContainerRequestContext requestContext) {
      final String header = requestContext.getHeaderString(AUTHORIZATION_HEADER);
      if (header == null || !header.startsWith(TOKEN_PREFIX)) {
         return null;
      }
      return header.substring(TOKEN_PREFIX.length()).trim();
   }

   private static boolean isPublic(final String path) {
      final String normalised = path.startsWith("/") ? path.substring(1) : path;
      return PUBLIC_PATHS.stream().anyMatch(normalised::startsWith);
   }

   /**
    * Erzeugt den Sicherheitskontext für den angemeldeten Benutzer.
    *
    * Die Rollenprüfung wird auf die Gruppenzugehörigkeit abgebildet, sodass
    * {@link SecurityContext#isUserInRole(String)} in den Endpunkten genutzt
    * werden kann.
    *
    * @param user Der angemeldete Benutzer
    * @param previous Der bisherige Sicherheitskontext der Anfrage
    * @return Der Sicherheitskontext mit dem angemeldeten Benutzer
    */
   private static SecurityContext createSecurityContext(final AuthenticatedUser user, final SecurityContext previous) {
      final boolean secure = previous != null && previous.isSecure();
      return new SecurityContext() {

         @Override
         public Principal getUserPrincipal() {
            return user;
         }

         @Override
         public boolean isUserInRole(final String role) {
            return user.isMemberOf(role);
         }

         @Override
         public boolean isSecure() {
            return secure;
         }

         @Override
         public String getAuthenticationScheme() {
            return "Bearer";
         }
      };
   }
}
