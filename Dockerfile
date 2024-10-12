# Usa la imagen base de OpenJDK 17 para ejecutar aplicaciones Java
FROM openjdk:17-jdk-slim

# Establece el directorio de trabajo en el contenedor
WORKDIR /app

# Copia el archivo pom.xml y los archivos de configuración en el contenedor
COPY pom.xml /app

# Copia todo el código fuente al contenedor
COPY src /app/src

# Ejecuta Maven para compilar el proyecto
# Utilizamos una imagen ligera de Maven que se elimina al final para mantener la imagen final ligera
RUN apt-get update && apt-get install -y maven && \
    mvn clean install -DskipTests && \
    apt-get remove -y maven && apt-get autoremove -y && rm -rf /var/lib/apt/lists/*

# Expone el puerto en el que la aplicación escucha (8081 en este caso)
EXPOSE 8081

# Define el comando para ejecutar la aplicación cuando el contenedor se inicie
CMD ["java", "-jar", "target/vertx-backend-1.0-SNAPSHOT-jar-with-dependencies.jar"]
