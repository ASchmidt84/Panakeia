# Panakeia

## Development

### Datenbanken

#### Postgresql

Die postgresql wird benutzt um alle repositories einzusetzen. Diese Datenbank muss angelegt werden. Die Zugangsdaten sind HIER zu finden!
Nun hier der Docker Befehl

```
docker run --name panakeia-ps \
-e POSTGRES_PASSWORD=panakeia \
-e POSTGRES_USER=panakeia \
-e POSTGRES_DB=panakeia \
--restart always \
-p 20000:5432 \
-d postgres
```

#### Cassandra

Die Cassandra ist für das EventSourcing zuständig. Das heißt hier wird der Zugriff eingestellt. Dafür nutze ich folgenden Link: https://www.datastax.com/blog/2012/01/getting-started-apache-cassandra. Damit die Cassandra läuft folgenden Befehl ausführen: D:\cassandra\apache-cassandra-3.11.6\bin\cassandra.bat
**Muss nicht in Powershell gemacht werden, es geht auch über CMD (aber als Administrator!)**
In Powershell bitte .\cassandra.bat ausühren! Vorher bitte powershell Set-ExecutionPolicy Unrestricted damit das ausgeführt werden

### Message Broker

#### Kafka

Damit die Events auch konsumiert werden können.

### Local Run (test)

In den Ordner D:\Projekte\Panakeia\security-impl\target\universal\security-impl-0.1.0-SNAPSHOT\bin die security-impl.bat ausführen (CMD). Dazu ist aber ein play secret nötig der aufruf ist also wie folgt
````
security-impl.bat -Dplay.http.secret.key=ad31779d4ee49d5ad5162bf1429c32e2e9933f3b
````
Im Prodmodus sollte es jedoch als application.conf vorliegen und so aufgerufen werden und dazu muss in der application-prod.conf natürlich aud der secret gesetzt werden!
```
security-impl.bat -Dconfig.file=/full/path/to/conf/application-prod.conf
```