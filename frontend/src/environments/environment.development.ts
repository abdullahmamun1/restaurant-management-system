/**
 * Development environment.
 *
 * `apiBaseUrl` is relative ('/api'); requests are forwarded to the Spring Boot backend
 * at http://localhost:8080 by the dev-server proxy (see proxy.conf.json). This avoids
 * cross-origin calls during development.
 */
export const environment = {
  production: false,
  apiBaseUrl: '/api'
};
