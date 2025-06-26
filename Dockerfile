# Stage 1: Cache Gradle dependencies
FROM gradle:latest AS cache
RUN mkdir -p /home/gradle/cache_home
ENV GRADLE_USER_HOME=/home/gradle/cache_home
COPY build.gradle.* gradle.properties /home/gradle/app/
COPY gradle /home/gradle/app/gradle
WORKDIR /home/gradle/app
RUN gradle clean build -i --stacktrace

# Stage 2: Build Application and Verify JAR
FROM gradle:latest AS build
COPY --from=cache /home/gradle/cache_home /home/gradle/.gradle
COPY . /usr/src/app/
WORKDIR /usr/src/app
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src

# Build the fat jar
RUN gradle buildFatJar --no-daemon

# ✅ Check that the JAR was created
RUN ls -lh build/libs/ && \
    test -f build/libs/*.jar

# ✅ Check if application.conf is inside the JAR
RUN jar tf build/libs/*.jar | grep -q "application.conf" \
    && echo "✅ application.conf is present in the JAR" \
    || (echo "❌ ERROR: application.conf not found in JAR" && exit 1)

# Stage 3: Create the Runtime Image
FROM openjdk:21 AS runtime
EXPOSE 8080
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*.jar /app/anop.jar
ENTRYPOINT ["java","-jar","/app/anop.jar"]
