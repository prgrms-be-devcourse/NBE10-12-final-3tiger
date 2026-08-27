FROM postgis/postgis:17-3.5

# pgRouting 3.x - v1.2 dump가 routing.walk_edges 등을 포함하므로 restore 전에 필요
RUN apt-get update \
 && apt-get install -y --no-install-recommends postgresql-17-pgrouting \
 && rm -rf /var/lib/apt/lists/*
