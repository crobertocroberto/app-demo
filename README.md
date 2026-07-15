# Demo CI/CD - Spring Boot + Jenkins

Aplicacion Spring Boot sencilla para demostrar un pipeline de CI/CD con Jenkins.

## Estructura del Proyecto

```
├── src/
│   ├── main/
│   │   ├── java/com/terrasys/democicd/
│   │   │   ├── DemoCicdApplication.java
│   │   │   └── controller/HealthController.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/com/terrasys/democicd/
│           └── DemoCicdApplicationTests.java
├── Dockerfile
├── Jenkinsfile
├── pom.xml
└── README.md
```

## Variables de Entorno

La aplicacion usa variables de entorno para la configuracion de base de datos:

| Variable    | Descripcion            |
|-------------|------------------------|
| DB_USER     | Usuario de la BD       |
| DB_PASSWORD | Password de la BD      |
| DB_HOST     | Host de la BD          |
| DB_NAME     | Nombre de la base      |

## Ejecutar Localmente

```bash
# Con variables de entorno
export DB_USER=admin
export DB_PASSWORD=secret
export DB_HOST=localhost
export DB_NAME=mydb

# Compilar y ejecutar
mvn clean package -DskipTests
java -jar target/demo-cicd-1.0.0.jar
```

## Ejecutar con Docker

```bash
docker build -t terrasys/demo-cicd .

docker run -d \
  -p 8080:8080 \
  -e DB_USER=admin \
  -e DB_PASSWORD=secret \
  -e DB_HOST=localhost \
  -e DB_NAME=mydb \
  terrasys/demo-cicd
```

## Endpoints

- `GET /` - Info de la aplicacion
- `GET /health` - Estado de salud con info de BD

## Pipeline de Jenkins

El `Jenkinsfile` define las siguientes etapas:

1. **Checkout** - Clona el repositorio
2. **Build** - Compila el codigo fuente
3. **Test** - Ejecuta tests unitarios
4. **Package** - Genera el JAR
5. **Docker Build** - Construye la imagen Docker
6. **Docker Push** - Sube la imagen al registry
7. **Deploy** - Despliega el contenedor con las variables de entorno

### Credenciales necesarias en Jenkins

Configurar en Jenkins > Manage Credentials:

- `docker-registry-credentials` (Username/Password) - Credenciales del Docker registry
- `db-user` (Secret text) - Usuario de la BD
- `db-password` (Secret text) - Password de la BD
- `db-host` (Secret text) - Host de la BD
- `db-name` (Secret text) - Nombre de la BD

## Subir a GitHub

```bash
git init
git add .
git commit -m "Initial commit - Spring Boot CI/CD demo"
git branch -M main
git remote add origin https://github.com/tu-usuario/demo-cicd.git
git push -u origin main
```
