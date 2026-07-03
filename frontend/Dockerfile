# ---- build stage ----
FROM node:24-alpine AS build
WORKDIR /app
# VPS RAM thap: gioi han heap de ng build khong bi OOM-kill (exit 137)
ENV NODE_OPTIONS=--max-old-space-size=1024
COPY package*.json ./
RUN npm ci                     # cache layer dependency
COPY . .
RUN npm run build

# ---- run stage ----
FROM nginx:alpine
# Angular 20 application builder xuat ra dist/frontend/browser
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
