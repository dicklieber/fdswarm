### To clear prometheus data

View prometheus data: http://localhost:9090/query
View grafana: http://localhost:3000/dashboards


```
docker compose down
docker volume rm fdswarm-observability_prometheus-data
docker compose up -d
```
Since Grafana queries prometheus, this will be reflected in Grafana also.

## Setup Prometheus  Scraping
This is usefule when running manager to start many local instances. 

Manager uses port starting at 8080. 

Assumes running an IntelliJ instance at 8079.
```cat prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: "fdswarm"
    metrics_path: /metrics
    static_configs:
      - targets:
          - "host.docker.internal:8079"
          - "host.docker.internal:8080"
          - "host.docker.internal:8081"
          - "host.docker.internal:8082"
          - "host.docker.internal:8083"
          - "host.docker.internal:8084"
          - "host.docker.internal:8085"
          - "host.docker.internal:8086"
          - "host.docker.internal:8087"
          - "host.docker.internal:8088"
          - "host.docker.internal:8089"%      ```