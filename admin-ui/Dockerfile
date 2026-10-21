FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY index.html styles.css app.js client.mjs /usr/share/nginx/html/
EXPOSE 8080
