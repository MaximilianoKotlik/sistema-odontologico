# syntax=docker/dockerfile:1
# Frontend multi-stage (design D9): dev (Vite, lo usa Compose) · build · prod (nginx sirviendo dist/).
# El contexto de build es la RAÍZ del repo (docker-compose.yml) porque el frontend importa
# design-system/tokens.css y design-system/brand.js sin copiarlos (C-06, design D5).
# Qué entra al contexto lo define frontend/Dockerfile.dockerignore.

FROM node:22-alpine AS deps
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

FROM deps AS fuentes
COPY design-system /app/design-system
COPY frontend/ ./

FROM fuentes AS dev
EXPOSE 5173
CMD ["npm", "run", "dev"]

FROM fuentes AS build
RUN npm run build

FROM nginx:1.27-alpine AS prod
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/frontend/dist /usr/share/nginx/html
EXPOSE 80
