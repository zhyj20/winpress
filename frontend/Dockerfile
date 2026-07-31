FROM node:22-alpine AS build

WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
ARG VITE_LOCAL_DEMO=false
ENV VITE_LOCAL_DEMO=${VITE_LOCAL_DEMO}
RUN npm run build

FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/conf.d/security-headers.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 80
