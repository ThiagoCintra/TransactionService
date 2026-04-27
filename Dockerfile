FROM eclipse-temurin:25-jdk-jammy AS build
# Install a specific Maven binary (avoid apt maven which pulls a different JRE) and keep Temurin 25 as the active JDK
RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl tar ca-certificates \
	&& rm -rf /var/lib/apt/lists/* \
	&& MAVEN_VERSION=3.9.6 \
	&& curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" -o /tmp/maven.tar.gz \
	&& tar -xzf /tmp/maven.tar.gz -C /opt \
	&& ln -s /opt/apache-maven-${MAVEN_VERSION} /opt/maven \
	&& rm /tmp/maven.tar.gz

ENV MAVEN_HOME=/opt/maven
ENV PATH="$MAVEN_HOME/bin:${PATH}"
WORKDIR /app

COPY pom.xml .
COPY src ./src

## Use maven.test.skip to avoid compiling/exec tests inside the image build
## This prevents failures from test compilation when building inside Docker.
RUN mvn -Dmaven.test.skip=true clean package -B

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl netcat-openbsd && rm -rf /var/lib/apt/lists/*

## Create a small wait-for script which checks TCP ports defined in WAIT_HOSTS (comma separated host:port)
RUN cat > /wait-for.sh << 'EOF'
#!/bin/sh
set -e
if [ -n "${WAIT_HOSTS:-}" ]; then
  IFS=','; for h in $WAIT_HOSTS; do
	host=$(echo "$h" | cut -d: -f1)
	port=$(echo "$h" | cut -d: -f2)
	echo "Waiting for $host:$port..."
	retries=30
	count=0
	until nc -z "$host" "$port"; do
	  count=$((count+1))
	  if [ "$count" -ge "$retries" ]; then
		echo "Timeout waiting for $host:$port" >&2; exit 1
	  fi
	  sleep 2
	done
  done
fi
exec "$@"
EOF
RUN chmod +x /wait-for.sh

## Copy the repackaged Spring Boot jar
COPY --from=build /app/target/transaction-service-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["/bin/sh", "-c", "/wait-for.sh && exec java -jar /app/app.jar"]