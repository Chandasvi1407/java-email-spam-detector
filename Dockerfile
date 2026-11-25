FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY src ./src
COPY public ./public

RUN mkdir -p out \
    && find src -name "*.java" > sources.txt \
    && javac --release 17 -encoding UTF-8 -d out @sources.txt \
    && rm sources.txt

EXPOSE 10000

CMD ["java", "-cp", "out", "web.SpamApiServer"]
