FROM nginx:stable-alpine@sha256:47928bf67eb62a1863cb08d9ecb52d3e704304ba614eaee17884204677702951
RUN mkdir -p /run/docker-read-only && chown 10001:10001 /run/docker-read-only
COPY docker-read-only.conf /etc/nginx/agent-proxy.conf
USER 10001:10001
ENTRYPOINT ["nginx", "-c", "/etc/nginx/agent-proxy.conf", "-g", "daemon off;"]
