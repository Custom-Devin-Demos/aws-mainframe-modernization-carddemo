# One image per job module:
#   ./mvnw -B package -pl jobs/cbtrn02c -am
#   docker build --build-arg JOB_MODULE=cbtrn02c -t carddemo-batch-cbtrn02c .
FROM eclipse-temurin:17-jre
ARG JOB_MODULE
RUN useradd --system --uid 10001 carddemo
COPY jobs/${JOB_MODULE}/target/${JOB_MODULE}-job.jar /app/job.jar
USER carddemo
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/job.jar"]
