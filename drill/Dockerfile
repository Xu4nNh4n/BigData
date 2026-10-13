FROM apache/drill:1.21.2

# Apache Drill needs the JDBC driver in jars/3rdparty before the RDBMS plugin loads.
USER root
ADD https://repo.maven.apache.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar /opt/drill/jars/3rdparty/mysql-connector-j-8.4.0.jar
RUN chmod 644 /opt/drill/jars/3rdparty/mysql-connector-j-8.4.0.jar
USER drilluser
