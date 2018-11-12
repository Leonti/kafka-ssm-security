FROM hseeberger/scala-sbt:8u171_2.12.6_1.1.6 AS base

FROM base AS builder
RUN mkdir -p /app
WORKDIR /app
COPY . .
RUN sbt "set test in assembly := {}" clean assembly

FROM openjdk:8-jre-alpine AS release
WORKDIR /app
COPY --from=builder /app/target/scala-2.12/*.jar app.jar
CMD ["java", "-jar", "app.jar"]
