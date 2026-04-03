export const environment = {
  production: false,
  /**
   * Относительный путь: запросы идут на тот же host:port, что и страница (например :4200),
   * dev-server по proxy.conf проксирует /v1 → backend. Так работает и с другого ПК по IP сервера.
   * Не задавайте http://localhost:8080 — в браузере это «localhost клиента», не контейнер.
   */
  apiBaseUrl: '/v1',
};
