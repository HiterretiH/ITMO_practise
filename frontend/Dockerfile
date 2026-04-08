FROM node:22-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
EXPOSE 7272
CMD ["npm", "run", "start", "--", "--host", "0.0.0.0", "--proxy-config", "src/proxy.conf.docker.json"]
