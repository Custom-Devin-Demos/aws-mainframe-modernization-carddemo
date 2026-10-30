# Container image for running the interest calculation job on AWS Batch / ECS Fargate.
FROM public.ecr.aws/docker/library/maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -q -B package

FROM public.ecr.aws/amazoncorretto/amazoncorretto:17-al2023
RUN dnf install -y awscli-2 && dnf clean all
WORKDIR /app
COPY --from=build /src/target/intcalc.jar /app/intcalc.jar
COPY aws/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh
ENTRYPOINT ["/app/entrypoint.sh"]
