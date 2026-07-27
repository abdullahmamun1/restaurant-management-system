/**
 * Production environment.
 *
 * `apiBaseUrl` is relative so the built SPA calls the API on the same origin it is
 * served from (reverse-proxied in deployment).
 */
export const environment = {
  production: true,
  apiBaseUrl: '/api'
};
