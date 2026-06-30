FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm install

COPY . .
ARG VITE_API_BASE_URL=http://localhost:18080
ARG VITE_BOT_API_BASE_URL=http://localhost:18082
ARG VITE_BOT_REQUEST_TIMEOUT_MS=80000
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_BOT_API_BASE_URL=${VITE_BOT_API_BASE_URL}
ENV VITE_BOT_REQUEST_TIMEOUT_MS=${VITE_BOT_REQUEST_TIMEOUT_MS}
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
