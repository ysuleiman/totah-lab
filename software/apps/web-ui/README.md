# Totah Lab Web UI

React and TypeScript workspace for investigating structures and binding pockets.

## Development

Start the API on port 8080, then:

```shell
npm install
npm run dev
```

Vite proxies `/api` requests to `http://localhost:8080`.

## Checks

```shell
npm run lint
npm test
npm run build
```

## TODO

- Wire `GET /api/pockets/{pocketId}/evidence` into the pocket details UI.
  Define web-UI-owned TypeScript types for `PocketEvidenceView`; display
  `PRESENT`, evaluated `EMPTY`, `NOT_EVALUATED`, `NOT_APPLICABLE`, and
  `FAILED` distinctly; retain origin, method/version, failure, and provenance
  details. Do not derive a combined evidence score or treat unavailable
  channels as empty results.
