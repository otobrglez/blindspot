# Blindspot

Blindspot is a web app that scans global streaming catalogues and real‑time demand signals to spot the “white spaces” your
content hasn’t reached yet. In seconds, it ranks regions with the highest upside, reveals exactly which titles
competitors host there, and projects the ROI of expanding or localising each show—all powered by a blend of live APIs,
static market data, and AI optimisation. Put simply: Blindspot turns guesswork into data‑backed expansion plans, so
executives can green‑light their next billion‑viewer market before anyone else sees it.

The Blindspot service is deployed to **production** at [blindspot.pinkstack.com](https://blindspot.pinkstack.com).

> [!NOTE] 
> This project was developed as part of the Disney Streaming Alliance Hackathon in July 2025 and is considered to be PoC - proof of concept.
> The system does not use any of the IP or systems that belong to the organisation or affiliated brands.

## Development

- This is a mono-repository that has backend and frontend bundled together into a single codebase.
- The project uses [devenv](https://devenv.sh/) for managing dependencies and development environment.
- Use [just](https://github.com/casey/just) command runner to build and deploy things.
- Please read the [justfile](./justfile) to learn about the build process and tasks.

```bash
just docker-build-all # will build all images
just docker-push-all # will push all images

just deploy-all # will build and deploy to the production cluster

```

## Architectular notes

- Backend - [`blindspot`](./src) - system is written in Scala 3, uses SBT as build system and ZIO framework.
- Frontend - [`blindspot-ui`](./blindspot-ui) is written in TypeScript and uses Vue.js with Astro Builds
- Data collection is conducted via the `refresh-just-watch` application that collects data and feeds it into the database. The collection system is designed in a way that can be rerun per hour/daily and runs in production via the Kubernetes [CronJob](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/).
- Blindspot uses PostgreSQL as a database.
- The API server is written in ZIO HTTP
- The application is running in production with the help of Kubernetes. The definitions can be found in [`k8s`](./k8s) folder.
- There is preconfigured [docker-compose.yml](docker/docker-compose.yml) that will help with the local development.
- Migrations are conducted via the Flyway library and are bundled into the API service and collector.


## Author
- [Oto Brglez](https://github.com/otobrglez)
