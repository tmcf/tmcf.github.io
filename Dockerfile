FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/uberjar/lcluster-app.jar /lcluster-app/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/lcluster-app/app.jar"]
