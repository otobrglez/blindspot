FROM node:24 AS build

ARG blindspot_env
ENV BLINDSPOT_ENV=$blindspot_env
ARG blindspot_version
ENV BLINDSPOT_VERSION=$blindspot_version
ARG blindspot_endpoint
ARG BLINDSPOT_ENDPOINT=$blindspot_endpoint

WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine AS runtime
COPY ./nginx/nginx.conf /etc/nginx/nginx.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
