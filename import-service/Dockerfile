# syntax=docker/dockerfile:1
# Production-oriented: two-stage, production dependencies only, non-root, static UI included.
# Build (from repo root): docker build -t import-service:local -f import-service/Dockerfile import-service

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
RUN npm run build

FROM node:22-alpine
RUN addgroup -g 10001 app && adduser -D -H -u 10001 -G app app
WORKDIR /app
ENV NODE_ENV=production
COPY package.json package-lock.json* ./
RUN npm ci --omit=dev && rm -rf /root/.npm
COPY --from=build /app/dist ./dist
COPY static ./static
RUN chown -R app:app /app
USER app
EXPOSE 3000
# Import binds 0.0.0.0 by default; health on /health
CMD ["node", "dist/server.js"]
