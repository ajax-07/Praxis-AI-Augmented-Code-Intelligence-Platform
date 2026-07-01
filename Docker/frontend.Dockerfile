# # ---- Stage 1: build ----
# FROM node:22-alpine AS build
# WORKDIR /app

# COPY Frontend/package.json Frontend/package-lock.json ./
# RUN npm ci

# COPY Frontend/ .
# RUN npm run build   # Vite outputs to /app/dist

# # ---- Stage 2: serve ----
# FROM nginx:1.27-alpine
# COPY --from=build /app/dist /usr/share/nginx/html
# COPY docker/nginx.conf /etc/nginx/conf.d/default.conf

# EXPOSE 80