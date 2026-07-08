# ─────────────────────────────────────────────────────────────────────────────
# Praxis Console — multi-stage, production image
#
# Stage 1 builds the static bundle with Node. Stage 2 serves it with nginx,
# which also reverse-proxies /api to the backend (see nginx.conf). Build context
# is this directory (Frontend/praxis-console).
# ─────────────────────────────────────────────────────────────────────────────

# ---- Stage 1: build ---------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

# Install deps first (cached until the lockfile changes). `npm ci` is
# reproducible: it installs exactly what package-lock.json pins.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# Then the source, then the type-checked production build (tsc && vite build).
COPY . .
RUN npm run build

# ---- Stage 2: serve ---------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

# SPA fallback + /api proxy + SSE-friendly buffering (see nginx.conf).
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=5 \
  CMD wget -qO- http://localhost/ > /dev/null 2>&1 || exit 1

# ─── Hot reload (dev only — NOT enabled) ─────────────────────────────────────
# The production image is a static bundle; it does not hot-reload. For live
# frontend reloads use the Vite dev server against bind-mounted source (see the
# commented `frontend` dev block in Docker/docker-compose.yml). In short:
#   - mount ./Frontend/praxis-console into a node:22-alpine container
#   - run: npm install && npm run dev -- --host 0.0.0.0
#   - Vite HMR pushes changes to the browser over a websocket; nginx is bypassed.
