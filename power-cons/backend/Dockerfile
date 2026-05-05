# Stage 1: build
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.6_7_1.10.11_3.6.4 AS builder

WORKDIR /app

# cache dependencies before copying source
COPY build.sbt .
COPY project ./project
RUN sbt update

# compile and stage
COPY app  ./app
COPY conf ./conf
RUN sbt stage

# Stage 2: runtime
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=builder /app/target/universal/stage .

EXPOSE 9000

CMD ["bin/power-cons-backend", \
     "-Dplay.evolutions.db.default.autoApply=true"]
